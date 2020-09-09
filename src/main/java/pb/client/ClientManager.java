package pb.client;


import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.logging.Logger;

import pb.Endpoint;
import pb.EndpointUnavailable;
import pb.Manager;
import pb.ProtocolAlreadyRunning;
import pb.Utils;
import pb.protocols.IRequestReplyProtocol;
import pb.protocols.Protocol;
import pb.protocols.keepalive.KeepAliveProtocol;
import pb.protocols.session.SessionProtocol;
import pb.server.IOThread;

/**
 * Manages the connection to the server and the client's state.
 * 
 * @see {@link pb.Manager}
 * @see {@link pb.Endpoint}
 * @see {@link pb.protocols.Protocol}
 * @see {@link pb.protocols.IRequestReplyProtocol}
 * @author aaron
 *
 */


public class ClientManager extends Manager {
	private static Logger log = Logger.getLogger(ClientManager.class.getName());
	private SessionProtocol sessionProtocol;
	private KeepAliveProtocol keepAliveProtocol;
	private Socket socket;
	private String host;
	private int port;
	private int retries = 0;
	private volatile boolean reconnecting = false;

	public ClientManager(String host,int port)  {
		this.host = host;
		this.port = port;
		establishConnection();

	}

	/**
	 * Attempts to connect socket and run an endpoint.
	 * Failure to do so results in 10 attempts to reconnect.
	 */
	public void establishConnection() {
		try {
			//When socket connects successfully, reset retries, make sure reconnecting flag is false
			socket = new Socket(InetAddress.getByName(host), port);
			log.info("Socket connected successfully");
			reconnecting = false;
			retries = 0;
			Endpoint endpoint = new Endpoint(socket,this);
			endpoint.start();

			// simulate the client shutting down after 2mins
			// this will be removed when the client actually does something
			// controlled by the user
			Utils.getInstance().setTimeout(()->{
				try {
					sessionProtocol.stopSession();
				} catch (EndpointUnavailable e) {
					//ignore...
				}
			}, 120000);

			try {
				// just wait for this thread to terminate
				endpoint.join();
			} catch (InterruptedException e) {
				// just make sure the ioThread is going to terminate
				endpoint.close();
			}

			//Only cleans up timers if there isn't a timer set for reestablishConnection
			if (!reconnecting) {
				Utils.getInstance().cleanUp();
			}

		} catch (UnknownHostException e) {
			log.severe("Host unknown, socket not connected");
			e.printStackTrace();
		} catch (IOException e) {
			log.severe("Socket not connected, attempting to re-establish");
			//Reconnecting flags the program to not clean up timers while attempting to reconnect.
			reconnecting = true;
			reestablishConnection();

		}
	}


	/**
	 * Ten attempts are made to create the socket with the same host and port.
	 * Failure to connect cleans up timers and ends the program.
	 */
	public void reestablishConnection() {
		try {
			Thread.sleep(5000);
		} catch (InterruptedException e) {
			//do nothing
		}
		if (retries < 10) {
			retries++;
			log.info(String.format("Reestablishing connection attempt %d/10", retries));
			establishConnection();
		}
		else {
			log.info("Failed to reestablish connection");
			Utils.getInstance().cleanUp();
		}
	}

	/**
	 * The endpoint is ready to use.
	 * @param endpoint
	 */
	@Override
	public void endpointReady(Endpoint endpoint) {
		log.info("connection with server established");
		sessionProtocol = new SessionProtocol(endpoint, this);
		try {
			// we need to add it to the endpoint before starting it
			endpoint.handleProtocol(sessionProtocol);
			sessionProtocol.startAsClient();
		} catch (EndpointUnavailable e) {
			log.severe("connection with server terminated abruptly");
			endpoint.close();
		} catch (ProtocolAlreadyRunning e) {
			// hmmm, so the server is requesting a session start?
			log.warning("server initiated the session protocol... weird");
		}
		keepAliveProtocol = new KeepAliveProtocol(endpoint, this);
		try {
			// we need to add it to the endpoint before starting it
			endpoint.handleProtocol(keepAliveProtocol);
			keepAliveProtocol.startAsClient();
		} catch (EndpointUnavailable e) {
			log.severe("connection with server terminated abruptly");
			endpoint.close();
		} catch (ProtocolAlreadyRunning e) {
			// hmmm, so the server is requesting a session start?
			log.warning("server initiated the session protocol... weird");
		}
	}
	
	/**
	 * The endpoint close() method has been called and completed.
	 * @param endpoint
	 */
	public void endpointClosed(Endpoint endpoint) {
		log.info("connection with server terminated");
	}
	
	/**
	 * The endpoint has abruptly disconnected. It can no longer
	 * send or receive data.
	 * @param endpoint
	 */
	@Override
	public void endpointDisconnectedAbruptly(Endpoint endpoint) {
		log.severe("connection with server terminated abruptly");
		//Closes the disconnected endpoint, then begins reconnection process, trying 10 times.
		endpoint.close();
		reconnecting = true;
		reestablishConnection();

	}

	/**
	 * An invalid message was received over the endpoint.
	 * @param endpoint
	 */
	@Override
	public void endpointSentInvalidMessage(Endpoint endpoint) {
		log.severe("server sent an invalid message");
		endpoint.close();
	}
	

	/**
	 * The protocol on the endpoint is not responding.
	 * @param endpoint
	 */
	@Override
	public void endpointTimedOut(Endpoint endpoint,Protocol protocol) {
		log.severe("server has timed out");
		endpoint.close();
	}

	/**
	 * The protocol on the endpoint has been violated.
	 * @param endpoint
	 */
	@Override
	public void protocolViolation(Endpoint endpoint,Protocol protocol) {
		log.severe("protocol with server has been violated: "+protocol.getProtocolName());
		endpoint.close();
	}

	/**
	 * The session protocol is indicating that a session has started.
	 * @param endpoint
	 */
	@Override
	public void sessionStarted(Endpoint endpoint) {
		log.info("session has started with server");
		
		// we can now start other protocols with the server
	}

	/**
	 * The session protocol is indicating that the session has stopped. 
	 * @param endpoint
	 */
	@Override
	public void sessionStopped(Endpoint endpoint) {
		log.info("session has stopped with server");
		endpoint.close(); // this will stop all the protocols as well
	}
	

	/**
	 * The endpoint has requested a protocol to start. If the protocol
	 * is allowed then the manager should tell the endpoint to handle it
	 * using {@link pb.Endpoint#handleProtocol(Protocol)}
	 * before returning true.
	 * @param protocol
	 * @return true if the protocol was started, false if not (not allowed to run)
	 */
	@Override
	public boolean protocolRequested(Endpoint endpoint, Protocol protocol) {
		// the only protocols in this system are this kind...
		try {
			((IRequestReplyProtocol)protocol).startAsClient();
			endpoint.handleProtocol(protocol);
			return true;
		} catch (EndpointUnavailable e) {
			// very weird... should log this
			return false;
		} catch (ProtocolAlreadyRunning e) {
			// even more weird... should log this too
			return false;
		}
	}

}

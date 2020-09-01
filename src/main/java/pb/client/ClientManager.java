package pb.client;


import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.concurrent.CountDownLatch;
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

	public ClientManager(String host,int port) throws UnknownHostException, IOException {
		this.host = host;
		this.port = port;

		CountDownLatch countDownLatch = new CountDownLatch(10);
		try {
			socket = new Socket(InetAddress.getByName(host), port);
			log.info("Socket connected successfully");
		} catch (UnknownHostException e) {
			log.severe("Host unknown, socket not connected");
			e.printStackTrace();
		} catch (Exception e) {
			log.severe("Socket not connected, attempting to re-establish");
			reestablishConnection(countDownLatch);
		}
		try {
			countDownLatch.await();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

//		while (restablishingConnection) {
//			// do nothing
//		}



		//If reestablish connection fails, end program. No endpoint has been created so no
		//need to close it down.
		if (socket == null) {
			System.exit(-1);
		}

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
		
		Utils.getInstance().cleanUp();
	}
	/**
	 * Ten attempts are made to create the socket with the same host and port.
	 * Success resets the global retries and breaks from the loop.
	 * Failure to connect sleeps the thread for 5 seconds before trying again.
	 */
	public void reestablishConnection(CountDownLatch countDownLatch) {
		try {
			socket = new Socket(InetAddress.getByName(host), port);
			log.info("Connection established successfully");
			while (countDownLatch.getCount()!=0) {
				countDownLatch.countDown();
			}
		} catch (Exception e) {
			Utils.getInstance().setTimeout(() -> {
				if (countDownLatch.getCount()==0) {
					if (socket == null) {
						log.info("Connection establishment failed");
					}
				} else {
					log.info(String.format("Re-establishing connection, attempt %d/10", 10 - countDownLatch.getCount()));
					countDownLatch.countDown();
					reestablishConnection(countDownLatch);
				}
			}, 2000);
		}
	}



	/**
	 * The endpoint is ready to use.
	 * @param endpoint
	 */
	@Override
	public void endpointReady(Endpoint endpoint) {
		log.info("connection with server established");
		sessionProtocol = new SessionProtocol(endpoint,this);
		try {
			// we need to add it to the endpoint before starting it
			endpoint.handleProtocol(sessionProtocol);
			sessionProtocol.startAsClient();
		} catch (EndpointUnavailable e) {
			log.severe("connection with server terminated abruptly");
			//Attempts to reestablish connection. If it fails, closes the endpoint.
			reestablishConnection(new CountDownLatch(10));
			if (socket == null) {
				endpoint.close();
			}
		} catch (ProtocolAlreadyRunning e) {
			// hmmm, so the server is requesting a session start?
			log.warning("server initiated the session protocol... weird");
		}
		keepAliveProtocol = new KeepAliveProtocol(endpoint,this);
		try {
			// we need to add it to the endpoint before starting it
			endpoint.handleProtocol(keepAliveProtocol);
			keepAliveProtocol.startAsClient();
		} catch (EndpointUnavailable e) {
			log.severe("connection with server terminated abruptly");
			//Attempts to reestablish connection. If it fails, closes the endpoint.
			reestablishConnection(new CountDownLatch(10));
			if (socket == null) {
				endpoint.close();
			}
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
		//Attempts to reestablish connection. If it fails, closes the endpoint.
		reestablishConnection(new CountDownLatch(10));
		if (socket == null) {
			endpoint.close();
		}
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

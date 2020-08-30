package pb.protocols.keepalive;

import pb.Endpoint;
import pb.EndpointUnavailable;
import pb.Manager;
import pb.Utils;
import pb.protocols.IRequestReplyProtocol;
import pb.protocols.Message;
import pb.protocols.Protocol;

import java.util.logging.Logger;

/**
 * Provides all of the protocol logic for both client and server to undertake
 * the KeepAlive protocol. In the KeepAlive protocol, the client sends a
 * KeepAlive request to the server every 20 seconds using
 * {@link pb.Utils#setTimeout(pb.protocols.ICallback, long)}. The server must
 * send a KeepAlive response to the client upon receiving the request. If the
 * client does not receive the response within 20 seconds (i.e. at the next time
 * it is to send the next KeepAlive request) it will assume the server is dead
 * and signal its manager using
 * {@link pb.Manager#endpointTimedOut(Endpoint, Protocol)}. If the server does
 * not receive a KeepAlive request at least every 20 seconds (again using
 * {@link pb.Utils#setTimeout(pb.protocols.ICallback, long)}), it will assume
 * the client is dead and signal its manager. Upon initialisation, the client
 * should send the KeepAlive request immediately, whereas the server will wait
 * up to 20 seconds before it assumes the client is dead. The protocol stops
 * when a timeout occurs.
 *
 * @author aaron
 * @see {@link pb.Manager}
 * @see {@link pb.Endpoint}
 * @see {@link pb.protocols.Message}
 * @see {@link pb.protocols.keepalive.KeepAliveRequest}
 * @see {@link pb.protocols.keepalive.KeepaliveRespopnse}
 * @see {@link pb.protocols.Protocol}
 * @see {@link pb.protocols.IRequestReqplyProtocol}
 */
public class KeepAliveProtocol extends Protocol implements IRequestReplyProtocol {
    private static Logger log = Logger.getLogger(KeepAliveProtocol.class.getName());
    private static final int TWENTY_SECONDS = 20000;
    private volatile boolean receivedRequest = false;
    private volatile boolean recievedReply = false;


    /**
     * Name of this protocol.
     */
    public static final String protocolName = "KeepAliveProtocol";

    /**
     * Initialise the protocol with an endopint and a manager.
     *
     * @param endpoint
     * @param manager
     */
    public KeepAliveProtocol(Endpoint endpoint, Manager manager) {
        super(endpoint, manager);
    }

    /**
     * @return the name of the protocol
     */
    @Override
    public String getProtocolName() {
        return protocolName;
    }

    /**
     * If the protocol is stopped, then it reports this to the logger
     */
    @Override
    public void stopProtocol() {
        log.info("keep alive protocol stopped");
    }

    /*
     * Interface methods
     */

    /**
     *
     */
    public void startAsServer() {
        checkClientTimeout();
    }

    /**
     *
     */
    public void checkClientTimeout() {
        Utils.getInstance().setTimeout(() -> {
            if (!receivedRequest) {
                manager.endpointTimedOut(endpoint, this);
            } else {
                receivedRequest = false;
                checkClientTimeout();
            }
        }, TWENTY_SECONDS);
    }

    /**
     *
     */
    public void startAsClient() throws EndpointUnavailable {
        sendRequest(new KeepAliveRequest());
    }

    /**
     * @param msg
     */
    @Override
    public void sendRequest(Message msg) throws EndpointUnavailable {
        // Send the message to the server
        endpoint.send(msg);
        Utils.getInstance().setTimeout(() -> {
            try {
                // if the client hasn't received a reply from the server then report to manager
                if (!recievedReply) {
                    manager.endpointTimedOut(endpoint, this);
                } else {
                    // Server replied so we can send another request to the server
                    sendRequest(new KeepAliveRequest());
                    recievedReply = false;
                }
            } catch (EndpointUnavailable e) {
                log.severe("endpoint unavailable");
            }

        }, TWENTY_SECONDS);
    }

    /**
     * @param msg
     */
    @Override
    public void receiveReply(Message msg) {
        if (msg instanceof KeepAliveReply) {
            recievedReply = true;
            // Nothing more to do
        }
    }

    /**
     * @param msg
     * @throws EndpointUnavailable
     */
    @Override
    public void receiveRequest(Message msg) throws EndpointUnavailable {
        if (msg instanceof KeepAliveRequest) {
            // recognise that we have received a request and send a reply
            receivedRequest = true;
            sendReply(new KeepAliveReply());
        }
    }

    /**
     * @param msg
     */
    @Override
    public void sendReply(Message msg) throws EndpointUnavailable {
        endpoint.send(msg);
    }
}

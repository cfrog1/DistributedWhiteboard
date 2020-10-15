package pb;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.logging.Logger;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;


import pb.managers.ServerManager;
import pb.managers.endpoint.Endpoint;
import pb.utils.Utils;

/**
 * Simple whiteboard server to provide whiteboard peer notifications.
 *
 * @author aaron
 */
public class WhiteboardServer {
    private static Logger log = Logger.getLogger(WhiteboardServer.class.getName());

    /**
     * Emitted by a client to tell the server that a board is being shared. Argument
     * must have the format "host:port:boardid".
     * <ul>
     * <li>{@code args[0] instanceof String}</li>
     * </ul>
     */
    public static final String shareBoard = "SHARE_BOARD";

    /**
     * Emitted by a client to tell the server that a board is no longer being
     * shared. Argument must have the format "host:port:boardid".
     * <ul>
     * <li>{@code args[0] instanceof String}</li>
     * </ul>
     */
    public static final String unshareBoard = "UNSHARE_BOARD";

    /**
     * The server emits this event:
     * <ul>
     * <li>to all connected clients to tell them that a board is being shared</li>
     * <li>to a newly connected client, it emits this event several times, for all
     * boards that are currently known to be being shared</li>
     * </ul>
     * Argument has format "host:port:boardid"
     * <ul>
     * <li>{@code args[0] instanceof String}</li>
     * </ul>
     */
    public static final String sharingBoard = "SHARING_BOARD";

    /**
     * The server emits this event:
     * <ul>
     * <li>to all connected clients to tell them that a board is no longer
     * shared</li>
     * </ul>
     * Argument has format "host:port:boardid"
     * <ul>
     * <li>{@code args[0] instanceof String}</li>
     * </ul>
     */
    public static final String unsharingBoard = "UNSHARING_BOARD";

    /**
     * Emitted by the server to a client to let it know that there was an error in a
     * received argument to any of the events above. Argument is the error message.
     * <ul>
     * <li>{@code args[0] instanceof String}</li>
     * </ul>
     */
    public static final String error = "ERROR";

    /**
     * Default port number.
     */
    private static int port = Utils.indexServerPort;

    public static final List<String> sharedBoards = new ArrayList<String>(); //TODO methods to access this need to be synchronized

    private static void help(Options options) {
        String header = "PB Whiteboard Server for Unimelb COMP90015\n\n";
        String footer = "\ncontact aharwood@unimelb.edu.au for issues.";
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("pb.IndexServer", header, options, footer, true);
        System.exit(-1);
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        // set a nice log format
        System.setProperty("java.util.logging.SimpleFormatter.format",
                "[%1$tl:%1$tM:%1$tS:%1$tL] [%4$s] %2$s: %5$s%n");

        // parse command line options
        Options options = new Options();
        options.addOption("port", true, "server port, an integer");
        options.addOption("password", true, "password for server");


        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = null;
        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e1) {
            help(options);
        }

        if (cmd.hasOption("port")) {
            try {
                port = Integer.parseInt(cmd.getOptionValue("port"));
            } catch (NumberFormatException e) {
                System.out.println("-port requires a port number, parsed: " + cmd.getOptionValue("port"));
                help(options);
            }
        }

        // create a server manager and setup event handlers
        ServerManager serverManager;

        if (cmd.hasOption("password")) {
            serverManager = new ServerManager(port, cmd.getOptionValue("password"));
        } else {
            serverManager = new ServerManager(port);
        }

        /**
         * TODO: DEAL WITH ERROR
         */
        /*
        serverManager.on(shareBoard, (args2) -> {
            String board = (String) args2[0];
            // check error in board string host:port:boardID

            synchronized (sharedBoards) {
                sharedBoards.add(board);
            }
            serverManager.emit(sharingBoard, board);

        }).on(unshareBoard, (args2) -> {
            String board = (String) args2[0];
            synchronized (sharedBoards) {
                sharedBoards.remove(board);
            }
            serverManager.emit(unsharingBoard, board);

        }).on(ServerManager.sessionStarted, (args3) -> {
            Endpoint client = (Endpoint)args3[0];
            synchronized (sharedBoards) {
                sharedBoards.forEach(board -> client.emit(sharingBoard, board));
            }
        });
        */

        //alternative approach
        serverManager.on(ServerManager.sessionStarted, (args1) -> {
            //Provide new client with all currently shared boards
            Endpoint client = (Endpoint) args1[0];
            System.out.println("Hello World");
            synchronized (sharedBoards) {
                sharedBoards.forEach(board -> client.emit(sharingBoard, board));
            }
            //Client wants to share a board, share it to all other peers
            client.on(shareBoard, (args2) -> {
                System.out.println("args: " + args2[0]);
                String board = (String) args2[0];
                System.out.println(board);
                // check error in board string host:port:boardID, potentially make its own function
                String[] parts = board.split(":");
                if (parts.length != 3) {
                    client.emit(error, "incorrect board string"); //placeholder
                } else {
                    synchronized (sharedBoards) {
                        sharedBoards.add(board);
                    }
                    serverManager.emit(sharingBoard, board);
                }
                //Client wants to unshare a board, unshare to all other peers
            }).on(unshareBoard, (args2) -> {
                System.out.println("args: " + args2[0]);
                String board = (String) args2[0];
                // check error in board string host:port:boardID
                String[] parts = board.split(":");
                if (parts.length != 3) {
                    client.emit(error, "incorrect board string"); //placeholder
                } else {
                    synchronized (sharedBoards) {
                        sharedBoards.remove(board);
                    }
                    serverManager.emit(unsharingBoard, board);
                }
            });

            serverManager.on(sharingBoard, (args2) -> {
                String board = (String)args2[0];
                client.emit(sharingBoard, board);
            });

        });


        // start up the server
        log.info("Whiteboard Server starting up");
        serverManager.start();
        // nothing more for the main thread to do
        serverManager.join();
        Utils.getInstance().cleanUp();

    }

}

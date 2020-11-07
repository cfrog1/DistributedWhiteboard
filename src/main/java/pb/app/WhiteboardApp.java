package pb.app;

import pb.WhiteboardServer;
import pb.managers.ClientManager;
import pb.managers.PeerManager;
import pb.managers.ServerManager;
import pb.managers.endpoint.Endpoint;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.*;
import java.util.logging.Logger;


/**
 * Initial code obtained from:
 * https://www.ssaurel.com/blog/learn-how-to-make-a-swing-painting-and-drawing-application/
 */
public class WhiteboardApp {
	private static Logger log = Logger.getLogger(WhiteboardApp.class.getName());

	/**
	 * Emitted to another peer to subscribe to updates for the given board. Argument
	 * must have format "host:port:boardid".
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String listenBoard = "BOARD_LISTEN";

	/**
	 * Emitted to another peer to unsubscribe to updates for the given board.
	 * Argument must have format "host:port:boardid".
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String unlistenBoard = "BOARD_UNLISTEN";

	/**
	 * Emitted to another peer to get the entire board data for a given board.
	 * Argument must have format "host:port:boardid".
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String getBoardData = "GET_BOARD_DATA";

	/**
	 * Emitted to another peer to give the entire board data for a given board.
	 * Argument must have format "host:port:boardid%version%PATHS".
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String boardData = "BOARD_DATA";

	/**
	 * Emitted to another peer to add a path to a board managed by that peer.
	 * Argument must have format "host:port:boardid%version%PATH". The numeric value
	 * of version must be equal to the version of the board without the PATH added,
	 * i.e. the current version of the board.
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String boardPathUpdate = "BOARD_PATH_UPDATE";

	/**
	 * Emitted to another peer to indicate a new path has been accepted. Argument
	 * must have format "host:port:boardid%version%PATH". The numeric value of
	 * version must be equal to the version of the board without the PATH added,
	 * i.e. the current version of the board.
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String boardPathAccepted = "BOARD_PATH_ACCEPTED";

	/**
	 * Emitted to another peer to remove the last path on a board managed by that
	 * peer. Argument must have format "host:port:boardid%version%". The numeric
	 * value of version must be equal to the version of the board without the undo
	 * applied, i.e. the current version of the board.
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String boardUndoUpdate = "BOARD_UNDO_UPDATE";

	/**
	 * Emitted to another peer to indicate an undo has been accepted. Argument must
	 * have format "host:port:boardid%version%". The numeric value of version must
	 * be equal to the version of the board without the undo applied, i.e. the
	 * current version of the board.
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String boardUndoAccepted = "BOARD_UNDO_ACCEPTED";

	/**
	 * Emitted to another peer to clear a board managed by that peer. Argument must
	 * have format "host:port:boardid%version%". The numeric value of version must
	 * be equal to the version of the board without the clear applied, i.e. the
	 * current version of the board.
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String boardClearUpdate = "BOARD_CLEAR_UPDATE";

	/**
	 * Emitted to another peer to indicate an clear has been accepted. Argument must
	 * have format "host:port:boardid%version%". The numeric value of version must
	 * be equal to the version of the board without the clear applied, i.e. the
	 * current version of the board.
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String boardClearAccepted = "BOARD_CLEAR_ACCEPTED";

	/**
	 * Emitted to another peer to indicate a board no longer exists and should be
	 * deleted. Argument must have format "host:port:boardid".
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String boardDeleted = "BOARD_DELETED";

	/**
	 * Emitted to another peer to indicate an error has occurred.
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String boardError = "BOARD_ERROR";

	/**
	 * White board map from board name to board object
	 */
	Map<String,Whiteboard> whiteboards;

	Map<String, Set<Endpoint>> boardEndpoints;

	Map<String, Endpoint> boardServerEndpoints;

	/**
	 * The currently selected white board
	 */
	Whiteboard selectedBoard = null;

	/**
	 * The peer:port string of the peer. This is synonomous with IP:port, host:port,
	 * etc. where it may appear in comments.
	 */
	String peerport="standalone"; // a default value for the non-distributed version

	/*
	 * GUI objects, you probably don't need to modify these things... you don't
	 * need to modify these things... don't modify these things [LOTR reference?].
	 */

	JButton clearBtn, blackBtn, redBtn, createBoardBtn, deleteBoardBtn, undoBtn;
	JCheckBox sharedCheckbox ;
	DrawArea drawArea;
	JComboBox<String> boardComboBox;
	boolean modifyingComboBox=false;
	boolean modifyingCheckBox=false;

	/**
	 * Initialize the white board app.
	 */

	Endpoint theWhiteBoardEndpoint;
	PeerManager thePeerManager;
	ClientManager theClientManager;

	//TODO: make sure that all peerStarted events also have peerStopped, peerError etc.
	public WhiteboardApp(int peerPort, String whiteboardServerHost,
						 int whiteboardServerPort) {
		whiteboards = new HashMap<>();
		boardEndpoints = new HashMap<>();
		boardServerEndpoints = new HashMap<>();
		peerport = whiteboardServerHost + ":" + peerPort;
		System.out.println("peer port: " + peerPort);
		PeerManager peerManager = new PeerManager(peerPort);

		//Connect to server
		try {
			ClientManager clientManager = peerManager.connect(whiteboardServerPort, whiteboardServerHost);
			theClientManager = clientManager;
			clientManager.on(PeerManager.peerStarted, (args) -> {
				Endpoint endpoint = (Endpoint)args[0];

				theWhiteBoardEndpoint = endpoint;

				shareToggleEmit(endpoint); //Set up events for when 'shared' is checked on a local whiteboard
				log.info("Connected to server");

				//When a sharingBoard event is received, connect to the board's owner
				endpoint.on(WhiteboardServer.sharingBoard, (args2) -> {
					String board = (String) args2[0];
					log.info("Received sharingBoard event: "+board);
					if (!whiteboards.containsKey(board)) {
						try {
							ClientManager clientPeerManager = peerManager.connect(getPort(board), getIP(board));

							//When connected to board's owner, setup listeners for updates
							clientPeerManager.on(PeerManager.peerStarted, (args3) -> {
								Endpoint peer = (Endpoint)args3[0];
								boardServerEndpoints.put(board, peer);
								log.info("Connected to peer: "+peer.getOtherEndpointId());

								peer.on(boardData, (args4) -> {
									String boardData = (String)args4[0];
									log.info("Received full board data for board: "+getBoardName(boardData));
									Whiteboard whiteboard = new Whiteboard(getBoardName(boardData), true);
									whiteboard.whiteboardFromString(getBoardName(boardData), getBoardData(boardData));
									addBoard(whiteboard, false);
									if (whiteboard.equals(selectedBoard)) {
										drawSelectedWhiteboard();
									}
									peer.emit(listenBoard, whiteboard.getName());

								}).on(boardPathAccepted, (args5) -> {
									String pathUpdate = (String)args5[0];
									log.info("Received new path update from board owner: "+pathUpdate);
									Whiteboard whiteboard = whiteboards.get(getBoardName(pathUpdate));
									WhiteboardPath newPath = new WhiteboardPath(getBoardPaths(pathUpdate));
									long oldVersion = getBoardVersion(pathUpdate);

									if (!whiteboard.addPath(newPath, oldVersion)) {
										peer.emit(getBoardData, getBoardName(pathUpdate));
									}
									if (whiteboard.equals(selectedBoard)) {
										drawSelectedWhiteboard();
									}
								}).on(boardClearAccepted, (args6) -> {
									String clearUpdate = (String)args6[0];
									log.info("Received new clear update from board owner: "+clearUpdate);
									Whiteboard whiteboard = whiteboards.get(getBoardName(clearUpdate));
									long oldVersion = getBoardVersion(clearUpdate);

									if (!whiteboard.clear(oldVersion)) {
										peer.emit(getBoardData, getBoardName(clearUpdate));
									}
									if (whiteboard.equals(selectedBoard)) {
										drawSelectedWhiteboard();
									}
								}).on(boardUndoAccepted, (args6) -> {
									String undoUpdate = (String)args6[0];
									log.info("Received new undo update from board owner: "+undoUpdate);
									Whiteboard whiteboard = whiteboards.get(getBoardName(undoUpdate));
									long oldVersion = getBoardVersion(undoUpdate);

									if (!whiteboard.undo(oldVersion)) {
										peer.emit(getBoardData, getBoardName(undoUpdate));
									}
									if (whiteboard.equals(selectedBoard)) {
										drawSelectedWhiteboard();
									}
								}).on(boardDeleted, (args7) -> {
									// we need to remove the board from selection
									String deleteUpdate = (String) args7[0];
									log.info("Received new undo update from board owner: " + deleteUpdate);
									deleteBoard(getBoardName(deleteUpdate));
									clientPeerManager.shutdown();
								});

								peer.emit(getBoardData, board);
								log.info("Requested full board data for: "+board);

							}).on(PeerManager.peerStopped, (args3) -> {
								Endpoint peer = (Endpoint)args3[0];
								log.info("Peer stopped with board owner: "+peer.getOtherEndpointId());
								whiteboards.remove(board);
								clientManager.sessionStopped(peer);
							}).on(PeerManager.peerError, (args3) -> {
								Endpoint peer = (Endpoint)args3[0];
								log.severe("Peer error with board owner: "+peer.getOtherEndpointId());
								whiteboards.remove(board);
							});

							clientPeerManager.start();
							//clientPeerManager.join();

						} catch (UnknownHostException e) {
							log.severe("Unable to connect to host:port provided");
							e.printStackTrace();
						} catch (InterruptedException e) {
							e.printStackTrace();
						}

					}
					//When an unsharingBoard event is received, remove it from out list of boards
				}).on(WhiteboardServer.unsharingBoard, (args3 -> {
					String board = (String) args3[0];
					log.info("Board owner no longer sharing board: "+board);
					synchronized (whiteboards) {
						if (whiteboards.containsKey(board)) {
							Whiteboard whiteboard = whiteboards.get(board);
							if (whiteboard.isRemote()) {
								deleteBoard(board);
							}
						}
					}
				}));
			}).on(PeerManager.peerStopped, (args3) -> {
				Endpoint endpoint = (Endpoint)args3[0];
				log.info("Peer stopped with wb server: "+endpoint.getOtherEndpointId());

			}).on(PeerManager.peerError, (args3) -> {
				Endpoint endpoint = (Endpoint)args3[0];
				log.severe("Peer error with wb server: "+endpoint.getOtherEndpointId());
			});

			thePeerManager = peerManager;
			//The whiteboard's server events
			peerManager.on(PeerManager.peerServerManager, (args) -> {
				ServerManager serverManager = (ServerManager)args[0];

				//When a peer connects, listen to any events they send
				serverManager.on(ServerManager.sessionStarted, (args2) -> {
					Endpoint client = (Endpoint)args2[0];
					log.info("Peer has connected from: "+client.getOtherEndpointId());

					//When getBoardData is received, send all data about the board back to the peer.
					client.on(getBoardData, (args3) -> {
						String board = (String)args3[0];
						log.info("getBoardData request received about board: "+board);
						Whiteboard requestedBoard = whiteboards.get(board);
						client.emit(boardData, requestedBoard.toString());
						log.info("Board data emitted to peer: "+client.getOtherEndpointId());

					}).on(listenBoard, (args4) -> {
						String board = (String)args4[0];
						log.info("Peer wants to listen to updates for board: "+board);
						boardEndpoints.putIfAbsent(board, new HashSet<>());
						boardEndpoints.get(board).add(client);

					}).on(boardPathUpdate, (args5) -> {
						String pathUpdate = (String)args5[0];
						log.info("path update request received: "+pathUpdate);
						Whiteboard whiteboard = whiteboards.get(getBoardName(pathUpdate));
						String newPath = getBoardPaths(pathUpdate);
						long oldVersion = getBoardVersion(pathUpdate);

						if (whiteboard.addPath(new WhiteboardPath(newPath), oldVersion)) {
							if (boardEndpoints.containsKey(getBoardName(pathUpdate))){
								for (Endpoint endpoint : boardEndpoints.get(getBoardName(pathUpdate))) {
									endpoint.emit(boardPathAccepted, pathUpdate);
									log.info("New path update accepted");
								}
							}
						}
						if (whiteboard.equals(selectedBoard)) {
							drawSelectedWhiteboard();
						}
					}).on(boardClearUpdate, (args6) -> {
						String clearUpdate = (String) args6[0];
						log.info("path clear request received: "+clearUpdate);
						Whiteboard whiteboard = whiteboards.get(getBoardName(clearUpdate));
						long oldVersion = getBoardVersion(clearUpdate);
						if (whiteboard.clear(oldVersion)) {
							if (boardEndpoints.containsKey(getBoardName(clearUpdate))){
								for (Endpoint endpoint : boardEndpoints.get(getBoardName(clearUpdate))) {
									endpoint.emit(boardClearAccepted, clearUpdate);
									log.info("New clear update accepted");
								}
							}
						}
						if (whiteboard.equals(selectedBoard)) {
							drawSelectedWhiteboard();
						}
					}).on(boardUndoUpdate, (args6) -> {

						String undoUpdate = (String) args6[0];
						log.info("path undo request received: "+undoUpdate);
						Whiteboard whiteboard = whiteboards.get(getBoardName(undoUpdate));
						long oldVersion = getBoardVersion(undoUpdate);
						if (whiteboard.undo(oldVersion)) {
							if (boardEndpoints.containsKey(getBoardName(undoUpdate))){
								for (Endpoint endpoint : boardEndpoints.get(getBoardName(undoUpdate))) {
									endpoint.emit(boardUndoAccepted, undoUpdate);
									log.info("New undo update accepted");
								}
							}
						}
						if (whiteboard.equals(selectedBoard)) {
							drawSelectedWhiteboard();
						}
					}).on(unlistenBoard, (args7) -> {
						String unlistenUpdate = (String)args7[0];
						log.info("Peer wants to unlisten to updates for board: "+ unlistenUpdate);
						if (boardEndpoints.containsKey(getBoardName(unlistenUpdate))) {
							boardEndpoints.get(getBoardName(unlistenUpdate)).remove(client);
						}
					});
				}).on(ServerManager.sessionStopped, (args3) -> {
					Endpoint endpoint = (Endpoint)args3[0];
					log.info("Session stopped with peer: "+endpoint.getOtherEndpointId());
					for (String board : boardEndpoints.keySet()) {
						boardEndpoints.get(board).remove(endpoint);
					}

				}).on(ServerManager.sessionError, (args3) -> {
					Endpoint endpoint = (Endpoint)args3[0];
					log.severe("Session error with peer: "+endpoint.getOtherEndpointId());
					for (String board : boardEndpoints.keySet()) {
						boardEndpoints.get(board).remove(endpoint);
					}
				});

			});

			show(peerport);
			peerManager.start();
			clientManager.start();
			//clientManager.join();
			peerManager.joinWithClientManagers();
		} catch (Exception e) {
			log.severe("Unable to connect to whiteboard server");
			e.printStackTrace();
		}

	}

	/******
	 *
	 * Utility methods to extract fields from argument strings.
	 *
	 ******/

	/**
	 *
	 * @param data = peer:port:boardid%version%PATHS
	 * @return peer:port:boardid
	 */
	public static String getBoardName(String data) {
		String[] parts=data.split("%",2);
		return parts[0];
	}

	/**
	 *
	 * @param data = peer:port:boardid%version%PATHS
	 * @return boardid%version%PATHS
	 */
	public static String getBoardIdAndData(String data) {
		String[] parts=data.split(":");
		return parts[2];
	}

	/**
	 *
	 * @param data = peer:port:boardid%version%PATHS
	 * @return version%PATHS
	 */
	public static String getBoardData(String data) {
		String[] parts=data.split("%",2);
		return parts[1];
	}

	/**
	 *
	 * @param data = peer:port:boardid%version%PATHS
	 * @return version
	 */
	public static long getBoardVersion(String data) {
		String[] parts=data.split("%",3);
		return Long.parseLong(parts[1]);
	}

	/**
	 *
	 * @param data = peer:port:boardid%version%PATHS
	 * @return PATHS
	 */
	public static String getBoardPaths(String data) {
		String[] parts=data.split("%",3);
		return parts[2];
	}

	/**
	 *
	 * @param data = peer:port:boardid%version%PATHS
	 * @return peer
	 */
	public static String getIP(String data) {
		String[] parts=data.split(":");
		return parts[0];
	}

	/**
	 *
	 * @param data = peer:port:boardid%version%PATHS
	 * @return port
	 */
	public static int getPort(String data) {
		String[] parts=data.split(":");
		return Integer.parseInt(parts[1]);
	}

	/******
	 *
	 * Methods called from events.
	 *
	 ******/

	// From whiteboard server



	// From whiteboard peer
	private void shareToggleEmit(Endpoint endpoint) {
		sharedCheckbox.addItemListener(e -> {
			if (e.getStateChange() == ItemEvent.SELECTED) {
				endpoint.emit(WhiteboardServer.shareBoard, selectedBoard.getName());
				log.info("shareBoard event emitted to server");
			} else {
				endpoint.emit(WhiteboardServer.unshareBoard, selectedBoard.getName());
			}
		});
	}


	/******
	 *
	 * Methods to manipulate data locally. Distributed systems related code has been
	 * cut from these methods.
	 *
	 ******/

	/**
	 * Wait for the peer manager to finish all threads.
	 */
	public void waitToFinish() {

	}

	/**
	 * Add a board to the list that the user can select from. If select is
	 * true then also select this board.
	 * @param whiteboard
	 * @param select
	 */
	public void addBoard(Whiteboard whiteboard,boolean select) {
		synchronized(whiteboards) {
			whiteboards.put(whiteboard.getName(), whiteboard);
		}
		updateComboBox(select?whiteboard.getName():null);
	}

	/**
	 * Delete a board from the list.
	 * @param boardname must have the form peer:port:boardid
	 */
	public void deleteBoard(String boardname) {
		synchronized(whiteboards) {
			Whiteboard whiteboard = whiteboards.get(boardname);
			if(whiteboard!=null) {
				// If the board is a local board, then we need to let the other peers know that its been deleted
				if (!whiteboard.isRemote()) {
					if (boardEndpoints.containsKey(whiteboard.getName())) {
						for (Endpoint endpoint : boardEndpoints.get(whiteboard.getName())) {
							endpoint.emit(boardDeleted, boardname);
							log.info("new board path deleted sent to: " + endpoint.getName());
						}
						// no longer need to track who listens to this board (ie its no one anymore)
						boardEndpoints.remove(whiteboard.getName());
					}
				}

				// If the board is remote then we should probably notify the owner that we are no longer listening
				if (whiteboard.isRemote()) {
					Endpoint peer = boardServerEndpoints.get(boardname);

					// no longer need to track the board as a board that we listen to
					boardServerEndpoints.remove(boardname);
					peer.emit(unlistenBoard, boardname);
					log.info("Sending unlisten board to board owner");
				}
				whiteboards.remove(boardname);
			}
		}
		updateComboBox(null);
	}

	/**
	 * Create a new local board with name peer:port:boardid.
	 * The boardid includes the time stamp that the board was created at.
	 */
	public void createBoard() {
		String name = peerport+":board"+Instant.now().toEpochMilli();
		Whiteboard whiteboard = new Whiteboard(name,false);
		addBoard(whiteboard,true);
	}

	/**
	 * Add a path to the selected board. The path has already
	 * been drawn on the draw area; so if it can't be accepted then
	 * the board needs to be redrawn without it.
	 * @param currentPath
	 */
	public void pathCreatedLocally(WhiteboardPath currentPath) {
		if(selectedBoard!=null) {
			if(!selectedBoard.addPath(currentPath,selectedBoard.getVersion())) {
				// some other peer modified the board in between
				drawSelectedWhiteboard(); // just redraw the screen without the path
			} else {
				// was accepted locally, so do remote stuff if needed
				long oldVersion = selectedBoard.getVersion() - 1;
				String pathUpdate = selectedBoard.getName() + "%" + oldVersion + "%" + currentPath.toString();
				if(selectedBoard.isRemote()) {
					Endpoint peer = boardServerEndpoints.get(selectedBoard.getName());
					peer.emit(boardPathUpdate, pathUpdate);
					log.info("Sending board update request to board owner");
					//host:port:boardid%version%PATH
				}
				else {
					if (boardEndpoints.containsKey(selectedBoard.getName())) {
						for (Endpoint endpoint : boardEndpoints.get(selectedBoard.getName())) {
							endpoint.emit(boardPathAccepted, pathUpdate);
							log.info("New board path update accepted");
						}
					}
				}


			}
		} else {
			log.severe("path created without a selected board: "+currentPath);
		}
	}

	/**
	 * Clear the selected whiteboard.
	 */
	public void clearedLocally() {
		if(selectedBoard!=null) {
			if(!selectedBoard.clear(selectedBoard.getVersion())) {
				// some other peer modified the board in between
				drawSelectedWhiteboard();
			} else {
				// was accepted locally, so do remote stuff if needed
				long oldVersion = selectedBoard.getVersion() - 1;
				//host:port:boardid%version%
				String clearUpdate = selectedBoard.getName() + "%" + oldVersion + "%";
				if(selectedBoard.isRemote()) {
					Endpoint peer = boardServerEndpoints.get(selectedBoard.getName());
					peer.emit(boardClearUpdate, clearUpdate);
					log.info("Sending board clear request to board owner");
				}
				else {
					if (boardEndpoints.containsKey(selectedBoard.getName())) {
						for (Endpoint endpoint : boardEndpoints.get(selectedBoard.getName())) {
							endpoint.emit(boardClearAccepted, clearUpdate);
							log.info("New board clear update accepted");
						}
					}
				}
				drawSelectedWhiteboard();
			}
		} else {
			log.severe("cleared without a selected board");
		}
	}

	/**
	 * Undo the last path of the selected whiteboard.
	 */
	public void undoLocally() {

		if(selectedBoard!=null) {
			if(!selectedBoard.undo(selectedBoard.getVersion())) {
				// some other peer modified the board in between
				drawSelectedWhiteboard();
			} else {
				long oldVersion = selectedBoard.getVersion() - 1;
				// update update format: "host:port:boardid%version%"
				String undoUpdate = selectedBoard.getName() + "%" + oldVersion + "%";
				if(selectedBoard.isRemote()) {
					Endpoint peer = boardServerEndpoints.get(selectedBoard.getName());
					peer.emit(boardUndoUpdate, undoUpdate);
					log.info("Sending board undo request to board owner");
				}
				else {
					if (boardEndpoints.containsKey(selectedBoard.getName())) {
						for (Endpoint endpoint : boardEndpoints.get(selectedBoard.getName())) {
							endpoint.emit(boardUndoAccepted, undoUpdate);
							log.info("New board clear update accepted");
						}
					}
				}
				drawSelectedWhiteboard();
			}
		} else {
			log.severe("undo without a selected board");
		}
	}

	/**
	 * The variable selectedBoard has been set.
	 */
	public void selectedABoard() {
		drawSelectedWhiteboard();
		log.info("selected board: "+selectedBoard.getName());
	}

	/**
	 * Set the share status on the selected board.
	 */
	public void setShare(boolean share) {
		if(selectedBoard!=null) {
			selectedBoard.setShared(share);
		} else {
			log.severe("there is no selected board");
		}
	}


	/**
	 * Called by the gui when the user closes the app.
	 */
	public void guiShutdown() {
		HashSet<Whiteboard> existingBoards = new HashSet<>(whiteboards.values());
		existingBoards.forEach((board) -> {
			if (board.isShared() && !board.isRemote()){
				theWhiteBoardEndpoint.emit(WhiteboardServer.unshareBoard, board.getName());
			}
			deleteBoard(board.getName());
		});
		thePeerManager.shutdown();
	}


	/******
	 *
	 * GUI methods and callbacks from GUI for user actions.
	 * You probably do not need to modify anything below here.
	 *
	 ******/

	/**
	 * Redraw the screen with the selected board
	 */
	public void drawSelectedWhiteboard() {
		drawArea.clear();
		if (selectedBoard != null) {
			selectedBoard.draw(drawArea);
		}
	}

	/**
	 * Setup the Swing components and start the Swing thread, given the
	 * peer's specific information, i.e. peer:port string.
	 */
	public void show(String peerport) {
		// create main frame
		JFrame frame = new JFrame("Whiteboard Peer: " + peerport);
		Container content = frame.getContentPane();
		// set layout on content pane
		content.setLayout(new BorderLayout());
		// create draw area
		drawArea = new DrawArea(this);

		// add to content pane
		content.add(drawArea, BorderLayout.CENTER);

		// create controls to apply colors and call clear feature
		JPanel controls = new JPanel();
		controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));

		/**
		 * Action listener is called by the GUI thread.
		 */
		ActionListener actionListener = new ActionListener() {

			public void actionPerformed(ActionEvent e) {
				if (e.getSource() == clearBtn) {
					clearedLocally();
				} else if (e.getSource() == blackBtn) {
					drawArea.setColor(Color.black);
				} else if (e.getSource() == redBtn) {
					drawArea.setColor(Color.red);
				} else if (e.getSource() == boardComboBox) {
					if (modifyingComboBox) return;
					if (boardComboBox.getSelectedIndex() == -1) return;
					String selectedBoardName = (String) boardComboBox.getSelectedItem();
					if (whiteboards.get(selectedBoardName) == null) {
						log.severe("selected a board that does not exist: " + selectedBoardName);
						return;
					}
					selectedBoard = whiteboards.get(selectedBoardName);
					// remote boards can't have their shared status modified
					if (selectedBoard.isRemote()) {
						sharedCheckbox.setEnabled(false);
						sharedCheckbox.setVisible(false);
					} else {
						modifyingCheckBox = true;
						sharedCheckbox.setSelected(selectedBoard.isShared());
						modifyingCheckBox = false;
						sharedCheckbox.setEnabled(true);
						sharedCheckbox.setVisible(true);
					}
					selectedABoard();
				} else if (e.getSource() == createBoardBtn) {
					createBoard();
				} else if (e.getSource() == undoBtn) {
					if (selectedBoard == null) {
						log.severe("there is no selected board to undo");
						return;
					}
					undoLocally();
				} else if (e.getSource() == deleteBoardBtn) {
					if (selectedBoard == null) {
						log.severe("there is no selected board to delete");
						return;
					}
					deleteBoard(selectedBoard.getName());
				}
			}
		};

		clearBtn = new JButton("Clear Board");
		clearBtn.addActionListener(actionListener);
		clearBtn.setToolTipText("Clear the current board - clears remote copies as well");
		clearBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
		blackBtn = new JButton("Black");
		blackBtn.addActionListener(actionListener);
		blackBtn.setToolTipText("Draw with black pen");
		blackBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
		redBtn = new JButton("Red");
		redBtn.addActionListener(actionListener);
		redBtn.setToolTipText("Draw with red pen");
		redBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
		deleteBoardBtn = new JButton("Delete Board");
		deleteBoardBtn.addActionListener(actionListener);
		deleteBoardBtn.setToolTipText("Delete the current board - only deletes the board locally");
		deleteBoardBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
		createBoardBtn = new JButton("New Board");
		createBoardBtn.addActionListener(actionListener);
		createBoardBtn.setToolTipText("Create a new board - creates it locally and not shared by default");
		createBoardBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
		undoBtn = new JButton("Undo");
		undoBtn.addActionListener(actionListener);
		undoBtn.setToolTipText("Remove the last path drawn on the board - triggers an undo on remote copies as well");
		undoBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
		sharedCheckbox = new JCheckBox("Shared");
		sharedCheckbox.addItemListener(new ItemListener() {
			public void itemStateChanged(ItemEvent e) {
				if (!modifyingCheckBox) setShare(e.getStateChange() == 1);
			}
		});
		sharedCheckbox.setToolTipText("Toggle whether the board is shared or not - tells the whiteboard server");
		sharedCheckbox.setAlignmentX(Component.CENTER_ALIGNMENT);


		// create a drop list for boards to select from
		JPanel controlsNorth = new JPanel();
		boardComboBox = new JComboBox<String>();
		boardComboBox.addActionListener(actionListener);


		// add to panel
		controlsNorth.add(boardComboBox);
		controls.add(sharedCheckbox);
		controls.add(createBoardBtn);
		controls.add(deleteBoardBtn);
		controls.add(blackBtn);
		controls.add(redBtn);
		controls.add(undoBtn);
		controls.add(clearBtn);

		// add to content pane
		content.add(controls, BorderLayout.WEST);
		content.add(controlsNorth, BorderLayout.NORTH);

		frame.setSize(600, 600);

		// create an initial board
		createBoard();

		// closing the application
		frame.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent windowEvent) {
				if (JOptionPane.showConfirmDialog(frame,
						"Are you sure you want to close this window?", "Close Window?",
						JOptionPane.YES_NO_OPTION,
						JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION) {
					guiShutdown();
					frame.dispose();
				}
			}
		});

		// show the swing paint result
		frame.setVisible(true);

	}

	/**
	 * Update the GUI's list of boards. Note that this method needs to update data
	 * that the GUI is using, which should only be done on the GUI's thread, which
	 * is why invoke later is used.
	 *
	 * @param select, board to select when list is modified or null for default
	 *                selection
	 */
	private void updateComboBox(String select) {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				modifyingComboBox = true;
				boardComboBox.removeAllItems();
				int anIndex = -1;
				synchronized (whiteboards) {
					ArrayList<String> boards = new ArrayList<String>(whiteboards.keySet());
					Collections.sort(boards);
					for (int i = 0; i < boards.size(); i++) {
						String boardname = boards.get(i);
						boardComboBox.addItem(boardname);
						if (select != null && select.equals(boardname)) {
							anIndex = i;
						} else if (anIndex == -1 && selectedBoard != null &&
								selectedBoard.getName().equals(boardname)) {
							anIndex = i;
						}
					}
				}
				modifyingComboBox = false;
				if (anIndex != -1) {
					boardComboBox.setSelectedIndex(anIndex);
				} else {
					if (whiteboards.size() > 0) {
						boardComboBox.setSelectedIndex(0);
					} else {
						drawArea.clear();
						createBoard();
					}
				}

			}
		});
	}

}

# DistributedWhiteboard
Whiteboard system that supports multiple whiteboards and multiple users concurrently. 

Built on framework provided by Aaron Harwood.

Run WhiteboardServer:
java -cp target/pb3-0.0.1-SNAPSHOT-jar-with-dependencies.jar pb.WhiteboardServer

Run WhiteboardPeer:
java -cp target/pb3-0.0.1-SNAPSHOT-jar-with-dependencies.jar pb.WhiteboardPeer -port SOME-PORT

The whiteboard server maintains details of currently active whiteboards and their owners to allow peers to connect to eachother.

module DistributedSystemsA1 {
	exports pb.protocols;
	exports pb;
	exports pb.managers;
	exports pb.protocols.event;
	exports pb.protocols.keepalive;
	exports pb.protocols.session;
	exports pb.utils;
	exports pb.managers.endpoint;

	requires commons.cli;
	requires java.logging;
	requires json.simple;
	requires org.apache.commons.codec;
}
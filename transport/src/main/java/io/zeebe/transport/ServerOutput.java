package io.zeebe.transport;

public interface ServerOutput
{

    boolean sendMessage(TransportMessage transportMessage);

    boolean sendResponse(ServerResponse response);
}

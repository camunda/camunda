package io.zeebe.transport;

public class RemoteAddress
{
    private final int streamId;
    private final SocketAddress addr;

    public RemoteAddress(int streamId, SocketAddress addr)
    {
        this.streamId = streamId;
        this.addr = addr;
    }
    public int getStreamId()
    {
        return streamId;
    }
    public SocketAddress getAddress()
    {
        return addr;
    }
}

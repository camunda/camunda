package io.zeebe.transport;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.zeebe.util.buffer.BufferWriter;

public class MockClientOutput implements ClientOutput
{

    protected final Queue<ClientRequest> requestsToReturn;
    protected final List<DirectBuffer> sentRequests = new ArrayList<>();

    public MockClientOutput()
    {
        this.requestsToReturn = new LinkedList<>();
    }

    public void addStubRequests(ClientRequest... requestsToReturn)
    {
        for (ClientRequest request : requestsToReturn)
        {
            this.requestsToReturn.add(request);
        }
    }

    @Override
    public boolean sendMessage(TransportMessage transportMessage)
    {
        throw new RuntimeException("not implemented yet");
    }

    @Override
    public ClientRequest sendRequest(RemoteAddress addr, BufferWriter writer)
    {
        final ClientRequest clientRequest = requestsToReturn.remove();

        if (clientRequest != null)
        {
            final MutableDirectBuffer buffer = new UnsafeBuffer(new byte[writer.getLength()]);
            writer.write(buffer, 0);
            sentRequests.add(buffer);
        }

        return clientRequest;
    }
}

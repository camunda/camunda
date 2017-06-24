package io.zeebe.transport.impl;

import org.agrona.DirectBuffer;

import io.zeebe.dispatcher.FragmentHandler;

public class ClientSendFailureHandler implements FragmentHandler
{
    private final TransportHeaderDescriptor transportHeaderDescriptor = new TransportHeaderDescriptor();
    private final RequestResponseHeaderDescriptor requestResponseHeaderDescriptor = new RequestResponseHeaderDescriptor();

    protected final ClientRequestPool requestPool;

    public ClientSendFailureHandler(ClientRequestPool requestPool)
    {
        this.requestPool = requestPool;
    }

    @Override
    public int onFragment(DirectBuffer buffer, int offset, int length, int streamId, boolean isMarkedFailed)
    {
        final int protocolId = transportHeaderDescriptor.wrap(buffer, offset).protocolId();
        if (protocolId == TransportHeaderDescriptor.REQUEST_RESPONSE)
        {
            requestResponseHeaderDescriptor.wrap(buffer, offset + TransportHeaderDescriptor.HEADER_LENGTH);
            final long requestId = requestResponseHeaderDescriptor.requestId();

            final ClientRequestImpl pendingRequest = requestPool.getOpenRequestById(requestId);
            if (pendingRequest != null)
            {
                System.out.println("Failing request " + requestId);
                // TODO: noch nicht so toll; wenn, dann sollte der Sende rhier eine Nachricht übergeben können
                pendingRequest.fail(new RuntimeException("Could not send request"));
            }
            else
            {

                System.out.println("Not failing request " + requestId);
            }

        }

        return CONSUME_FRAGMENT_RESULT;
    }

}

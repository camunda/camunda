package io.zeebe.test.broker.protocol.brokerapi;

import java.util.ArrayList;
import java.util.List;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import io.zeebe.dispatcher.ClaimedFragment;
import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.protocol.clientapi.ControlMessageRequestDecoder;
import io.zeebe.protocol.clientapi.ExecuteCommandRequestDecoder;
import io.zeebe.protocol.clientapi.MessageHeaderDecoder;
import io.zeebe.test.broker.protocol.MsgPackHelper;
import io.zeebe.transport.Channel;
import io.zeebe.transport.protocol.Protocols;
import io.zeebe.transport.protocol.TransportHeaderDescriptor;
import io.zeebe.transport.requestresponse.RequestResponseProtocolHeaderDescriptor;
import io.zeebe.transport.spi.TransportChannelHandler;
import io.zeebe.util.buffer.BufferWriter;

public class StubResponseChannelHandler implements TransportChannelHandler
{

    protected final TransportHeaderDescriptor transportHeaderDescriptor = new TransportHeaderDescriptor();
    protected final RequestResponseProtocolHeaderDescriptor protocolHeaderDescriptor = new RequestResponseProtocolHeaderDescriptor();

    protected final Dispatcher sendBuffer;
    protected final ClaimedFragment claimedFragment = new ClaimedFragment();

    protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    protected final List<ResponseStub<ExecuteCommandRequest>> cmdRequestStubs = new ArrayList<>();
    protected final List<ResponseStub<ControlMessageRequest>> controlMessageStubs = new ArrayList<>();
    protected final MsgPackHelper msgPackHelper;

    // can also be used for verification
    protected final List<Object> allRequests = new ArrayList<>();
    protected final List<ControlMessageRequest> controlMessageRequests = new ArrayList<>();
    protected final List<ExecuteCommandRequest> commandRequests = new ArrayList<>();


    public StubResponseChannelHandler(Dispatcher sendBuffer, MsgPackHelper msgPackHelper)
    {
        this.sendBuffer = sendBuffer;
        this.msgPackHelper = msgPackHelper;
    }

    @Override
    public void onChannelOpened(Channel transportChannel)
    {
    }

    @Override
    public void onChannelClosed(Channel transportChannel)
    {
    }

    @Override
    public void onChannelSendError(Channel transportChannel, DirectBuffer buffer, int offset, int length)
    {
        throw new RuntimeException("not implemented");
    }

    @Override
    public boolean onChannelReceive(Channel transportChannel, DirectBuffer buffer, int offset, int length)
    {

        // copy request to be able to verify it later
        final UnsafeBuffer copy = new UnsafeBuffer(new byte[length]);
        copy.putBytes(0, buffer, offset, length);

        transportHeaderDescriptor.wrap(copy, 0);
        final int protocolId = transportHeaderDescriptor.protocolId();

        if (protocolId == Protocols.REQUEST_RESPONSE)
        {
            protocolHeaderDescriptor.wrap(copy, TransportHeaderDescriptor.HEADER_LENGTH);

            final int requestResponseMessageOffset = TransportHeaderDescriptor.HEADER_LENGTH + RequestResponseProtocolHeaderDescriptor.HEADER_LENGTH;
            final int requestResponseMessageLength = length - TransportHeaderDescriptor.HEADER_LENGTH - RequestResponseProtocolHeaderDescriptor.HEADER_LENGTH;
            headerDecoder.wrap(copy, TransportHeaderDescriptor.HEADER_LENGTH + RequestResponseProtocolHeaderDescriptor.HEADER_LENGTH);

            boolean requestHandled = false;
            if (ExecuteCommandRequestDecoder.TEMPLATE_ID == headerDecoder.templateId())
            {
                final ExecuteCommandRequest request = new ExecuteCommandRequest(transportChannel.getStreamId(), msgPackHelper);
                request.wrap(copy, requestResponseMessageOffset, requestResponseMessageLength);
                commandRequests.add(request);
                allRequests.add(request);

                requestHandled = handleRequest(request, cmdRequestStubs, transportChannel.getStreamId());

            }
            else if (ControlMessageRequestDecoder.TEMPLATE_ID == headerDecoder.templateId())
            {
                final ControlMessageRequest request = new ControlMessageRequest(transportChannel.getStreamId(), msgPackHelper);
                request.wrap(copy, requestResponseMessageOffset, requestResponseMessageLength);
                controlMessageRequests.add(request);
                allRequests.add(request);

                requestHandled = handleRequest(request, controlMessageStubs, transportChannel.getStreamId());
            }

            if (!requestHandled)
            {
                throw new RuntimeException("no stub applies to request");
            }
            else
            {
                return true;
            }
        }
        else
        {
            throw new RuntimeException("Cannot handle messages of protocol " + protocolId);
        }
    }

    protected <T> boolean handleRequest(T request, List<? extends ResponseStub<T>> responseStubs, int channelId)
    {
        for (ResponseStub<T> stub : responseStubs)
        {
            if (stub.applies(request))
            {
                final MessageBuilder<T> responseWriter = stub.getResponseWriter();
                responseWriter.initializeFrom(request);
                final int responseLength = responseWriter.getLength() + TransportHeaderDescriptor.HEADER_LENGTH + RequestResponseProtocolHeaderDescriptor.HEADER_LENGTH;

                final long claimedOffset = sendBuffer.claim(claimedFragment, responseLength, channelId);
                if (claimedOffset < 0)
                {
                    throw new RuntimeException("Could not claim send buffer fragment");
                }

                // TODO: the usage (and reuse) of the descriptors is not so nice
                writeResponseToFragment(protocolHeaderDescriptor.connectionId(), protocolHeaderDescriptor.requestId(), responseWriter);
                claimedFragment.commit();
                return true;
            }
        }
        return false;
    }

    protected void writeResponseToFragment(long connectionId, long requestId, BufferWriter bodyWriter)
    {
        final MutableDirectBuffer buffer = claimedFragment.getBuffer();
        int offset = claimedFragment.getOffset();

        // transport protocol header
        transportHeaderDescriptor.wrap(buffer, offset)
            .protocolId(Protocols.REQUEST_RESPONSE);

        offset += TransportHeaderDescriptor.HEADER_LENGTH;

        // request/response protocol header
        protocolHeaderDescriptor.wrap(buffer, offset)
            .connectionId(connectionId)
            .requestId(requestId);

        offset += RequestResponseProtocolHeaderDescriptor.HEADER_LENGTH;

        bodyWriter.write(buffer, offset);

    }

    @Override
    public boolean onControlFrame(Channel transportChannel, DirectBuffer buffer, int offset, int length)
    {
        // ignore
        System.out.println("Stub: Ignoring control frame");
        return true;
    }

    public void addExecuteCommandRequestStub(ResponseStub<ExecuteCommandRequest> stub)
    {
        cmdRequestStubs.add(stub);
    }

    public void addControlMessageRequestStub(ResponseStub<ControlMessageRequest> stub)
    {
        controlMessageStubs.add(stub);
    }

    public List<ControlMessageRequest> getReceivedControlMessageRequests()
    {
        return controlMessageRequests;
    }

    public List<ExecuteCommandRequest> getReceivedCommandRequests()
    {
        return commandRequests;
    }

    public List<Object> getAllReceivedRequests()
    {
        return allRequests;
    }
}

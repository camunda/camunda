package io.zeebe.test.broker.protocol.brokerapi;

import java.util.ArrayList;
import java.util.List;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.zeebe.dispatcher.ClaimedFragment;
import io.zeebe.protocol.clientapi.ControlMessageRequestDecoder;
import io.zeebe.protocol.clientapi.ExecuteCommandRequestDecoder;
import io.zeebe.protocol.clientapi.MessageHeaderDecoder;
import io.zeebe.test.broker.protocol.MsgPackHelper;
import io.zeebe.transport.RemoteAddress;
import io.zeebe.transport.ServerOutput;
import io.zeebe.transport.ServerRequestHandler;
import io.zeebe.transport.ServerResponse;

public class StubResponseChannelHandler implements ServerRequestHandler
{

    protected final ClaimedFragment claimedFragment = new ClaimedFragment();

    protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    protected final List<ResponseStub<ExecuteCommandRequest>> cmdRequestStubs = new ArrayList<>();
    protected final List<ResponseStub<ControlMessageRequest>> controlMessageStubs = new ArrayList<>();
    protected final MsgPackHelper msgPackHelper;

    // can also be used for verification
    protected final List<Object> allRequests = new ArrayList<>();
    protected final List<ControlMessageRequest> controlMessageRequests = new ArrayList<>();
    protected final List<ExecuteCommandRequest> commandRequests = new ArrayList<>();

    protected ServerResponse response = new ServerResponse();


    public StubResponseChannelHandler(MsgPackHelper msgPackHelper)
    {
        this.msgPackHelper = msgPackHelper;
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

    @Override
    public boolean onRequest(ServerOutput output, RemoteAddress remoteAddress, DirectBuffer buffer, int offset, int length, long requestId)
    {
        final MutableDirectBuffer copy = new UnsafeBuffer(new byte[length]);
        copy.putBytes(0, buffer, offset, length);

        headerDecoder.wrap(copy, 0);

        boolean requestHandled = false;
        if (ExecuteCommandRequestDecoder.TEMPLATE_ID == headerDecoder.templateId())
        {
            final ExecuteCommandRequest request = new ExecuteCommandRequest(remoteAddress, msgPackHelper);

            request.wrap(copy, 0, length);
            commandRequests.add(request);
            allRequests.add(request);

            requestHandled = handleRequest(output, request, cmdRequestStubs, remoteAddress, requestId);

        }
        else if (ControlMessageRequestDecoder.TEMPLATE_ID == headerDecoder.templateId())
        {
            final ControlMessageRequest request = new ControlMessageRequest(remoteAddress, msgPackHelper);

            request.wrap(copy, 0, length);
            controlMessageRequests.add(request);
            allRequests.add(request);

            requestHandled = handleRequest(output, request, controlMessageStubs, remoteAddress, requestId);
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

    protected <T> boolean handleRequest(
            ServerOutput output,
            T request,
            List<? extends ResponseStub<T>> responseStubs,
            RemoteAddress requestSource,
            long requestId)
    {
        for (ResponseStub<T> stub : responseStubs)
        {
            if (stub.applies(request))
            {
                final MessageBuilder<T> responseWriter = stub.getResponseWriter();
                responseWriter.initializeFrom(request);

                response.reset()
                    .remoteAddress(requestSource)
                    .requestId(requestId)
                    .writer(responseWriter);

                return output.sendResponse(response);
            }
        }
        return false;
    }
}

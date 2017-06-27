package io.zeebe.client.impl;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.agrona.DirectBuffer;

import io.zeebe.client.impl.cmd.ClientErrorResponseHandler;
import io.zeebe.client.impl.cmd.ClientResponseHandler;
import io.zeebe.protocol.clientapi.MessageHeaderDecoder;
import io.zeebe.transport.ClientRequest;

public class ResponseFuture<R> implements Future<R>
{
    protected final ClientRequest request;
    protected final ClientResponseHandler<R> responseHandler;
    protected final ClientErrorResponseHandler errorResponseHandler = new ClientErrorResponseHandler();

    protected final MessageHeaderDecoder messageHeaderDecoder = new MessageHeaderDecoder();

    public ResponseFuture(ClientRequest request, ClientResponseHandler<R> responseHandler)
    {
        this.request = request;
        this.responseHandler = responseHandler;
    }

    @Override
    public R get() throws InterruptedException, ExecutionException
    {
        try
        {
            // TODO: what should be default timeout?
            return get(30, TimeUnit.SECONDS);
        }
        catch (TimeoutException e)
        {
            throw new RuntimeException(e);
        }
    }

    @Override
    public R get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException
    {
        try
        {
            final DirectBuffer rawResponse = request.get(timeout, unit);
            return decodeResponse(rawResponse);
        }
        catch (TimeoutException e)
        {
            final String exceptionMessage = String.format("Provided timeout of %s ms reached.", unit.toMillis(timeout));
            throw new TimeoutException(exceptionMessage);
        }
        finally
        {
            request.close();
        }
    }

    protected R decodeResponse(DirectBuffer rawResponse) throws ExecutionException
    {
        messageHeaderDecoder.wrap(rawResponse, 0);

        final int schemaId = messageHeaderDecoder.schemaId();
        final int templateId = messageHeaderDecoder.templateId();
        final int blockLength = messageHeaderDecoder.blockLength();
        final int version = messageHeaderDecoder.version();

        final int responseMessageOffset = messageHeaderDecoder.encodedLength();

        if (schemaId == responseHandler.getResponseSchemaId() && templateId == responseHandler.getResponseTemplateId())
        {
            return responseHandler.readResponse(rawResponse, responseMessageOffset, blockLength, version);
        }
        else
        {
            final Throwable exception = errorResponseHandler.createException(rawResponse, responseMessageOffset, blockLength, version);

            throw new ExecutionException(exception);
        }
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning)
    {
        throw new UnsupportedOperationException("Requests cannot be cancelled.");
    }

    @Override
    public boolean isCancelled()
    {
        return request.isCancelled();
    }

    @Override
    public boolean isDone()
    {
        return request.isDone();
    }

}

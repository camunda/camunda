package org.camunda.tngp.client.impl;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.agrona.DirectBuffer;
import org.agrona.LangUtil;
import org.camunda.tngp.client.impl.cmd.ClientErrorResponseHandler;
import org.camunda.tngp.client.impl.cmd.ClientResponseHandler;
import org.camunda.tngp.protocol.clientapi.MessageHeaderDecoder;
import org.camunda.tngp.transport.requestresponse.client.PooledTransportRequest;
import org.camunda.tngp.transport.requestresponse.client.RequestTimeoutException;

public class ResponseFuture<R> implements Future<R>
{
    protected final PooledTransportRequest request;
    protected final ClientResponseHandler<R> responseHandler;
    protected final ClientErrorResponseHandler errorResponseHandler = new ClientErrorResponseHandler();

    protected final MessageHeaderDecoder messageHeaderDecoder = new MessageHeaderDecoder();

    public ResponseFuture(PooledTransportRequest request, ClientResponseHandler<R> responseHandler)
    {
        this.request = request;
        this.responseHandler = responseHandler;
    }

    @Override
    public R get() throws InterruptedException, ExecutionException
    {
        R result = null;

        try
        {
            result = get(request.getRequestTimeout(), TimeUnit.MILLISECONDS);
        }
        catch (TimeoutException e)
        {
            LangUtil.rethrowUnchecked(e);
        }

        return result;
    }

    @Override
    public R get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException
    {
        R responseObject = null;

        try
        {
            final boolean responseAvailable = request.awaitResponse(timeout, unit);

            if (responseAvailable)
            {
                final DirectBuffer responseBuffer = request.getResponseBuffer();

                messageHeaderDecoder.wrap(responseBuffer, 0);

                final int schemaId = messageHeaderDecoder.schemaId();
                final int templateId = messageHeaderDecoder.templateId();
                final int blockLength = messageHeaderDecoder.blockLength();
                final int version = messageHeaderDecoder.version();

                final int responseMessageOffset = messageHeaderDecoder.encodedLength();

                if (schemaId == responseHandler.getResponseSchemaId() && templateId == responseHandler.getResponseTemplateId())
                {
                    responseObject = responseHandler.readResponse(responseBuffer, responseMessageOffset, blockLength, version);
                }
                else
                {
                    final Throwable exception = errorResponseHandler.createException(responseBuffer, responseMessageOffset, blockLength, version);

                    throw new ExecutionException(exception);
                }
            }
            else
            {
                final String exceptionMessage = String.format("Provided timeout of %s ms reached.", unit.toMillis(timeout));
                throw new TimeoutException(exceptionMessage);
            }
        }
        catch (RequestTimeoutException exception)
        {
            throw new TimeoutException(exception.getMessage());
        }
        finally
        {
            request.close();
        }

        return responseObject;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning)
    {
        throw new UnsupportedOperationException("Requests cannot be cancelled.");
    }

    @Override
    public boolean isCancelled()
    {
        return false;
    }

    @Override
    public boolean isDone()
    {
        return request.pollResponse();
    }

}

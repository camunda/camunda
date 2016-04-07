package org.camunda.tngp.taskqueue.impl.cmd;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import org.camunda.tngp.taskqueue.client.ClientCommand;
import org.camunda.tngp.taskqueue.client.cmd.AsyncResult;
import org.camunda.tngp.taskqueue.impl.TngpClientImpl;
import org.camunda.tngp.taskqueue.protocol.MessageHeaderDecoder;
import org.camunda.tngp.taskqueue.protocol.MessageHeaderEncoder;
import org.camunda.tngp.transport.requestresponse.client.PooledTransportRequest;
import org.camunda.tngp.transport.requestresponse.client.TransportConnection;
import org.camunda.tngp.transport.requestresponse.client.TransportConnectionPool;

import uk.co.real_logic.agrona.DirectBuffer;
import uk.co.real_logic.agrona.MutableDirectBuffer;

public abstract class AbstractCmdImpl<R> implements AsyncResult<R>, ClientCommand<R>
{
    protected static final int STATE_NEW = 1;
    protected static final int REQUEST_SENT = 2;
    protected static final int FAILED = 3;
    protected static final int RESPONSE_READ = 4;

    protected static final Charset CHARSET = StandardCharsets.UTF_8;

    protected final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();

    protected final TransportConnectionPool connectionPool;
    protected final TngpClientImpl client;
    protected PooledTransportRequest request;

    protected int state;

    protected R responseValue;

    protected final int responseSchemaId;
    protected final int responseTemplateId;

    public AbstractCmdImpl(TngpClientImpl client, int responseSchemaId, int responseTemplateId)
    {
        this.client = client;
        this.responseSchemaId = responseSchemaId;
        this.responseTemplateId = responseTemplateId;
        this.connectionPool = client.getConnectionPool();
        this.state = STATE_NEW;
    }

    @Override
    public R execute(TransportConnection connection)
    {
        return executeAsync(connection).await();
    }

    @Override
    public R execute()
    {
        if(state != STATE_NEW)
        {
            throw new IllegalStateException("Cannot execute cmd: not new.");
        }
        try(final TransportConnection connection = connectionPool.openConnection())
        {
            if(connection != null)
            {
                return executeAsync(connection).await();
            }
            else
            {
                state = FAILED;
                throw new RuntimeException("Request failed: no connection available.");
            }
        }
    }

    @Override
    public AsyncResult<R> executeAsync(TransportConnection connection)
    {
        if(state != STATE_NEW)
        {
            throw new IllegalStateException("Cannot execute cmd: not new.");
        }

        request = connection.openRequest(client.determineChannelForRequest(this), getRequestLength());
        if(request != null)
        {
            writeRequest(request.getClaimedRequestBuffer(), request.getClaimedOffset());
            request.commit();
            state = REQUEST_SENT;
            return this;
        }
        else
        {
            state = FAILED;
            throw new RuntimeException("Request failed.");
        }
    }

    @Override
    public R get()
    {
        R value = null;

        if(state == REQUEST_SENT)
        {
            if(request.isResponseAvailable())
            {
                try
                {
                    responseValue = readReponse(request.getResponseBuffer(), request.getResponseLength());
                    value = responseValue;
                    closeRequest();
                    state = RESPONSE_READ;
                }
                catch(RuntimeException e)
                {
                    state = FAILED;
                    throw e;
                }
            }
            else
            {
                throw new IllegalStateException("Response is not available. Call await() or poll() firt.");
            }
        }
        else if(state == STATE_NEW)
        {
            throw new IllegalStateException("Cannot get(), request not open.");
        }
        else if(state == FAILED)
        {
            throw new IllegalStateException("Command failed");
        }
        else if(state == RESPONSE_READ)
        {
            value = responseValue;
        }

        return value;
    }

    private void closeRequest()
    {
        request.close();
        request = null;
    }

    @Override
    public boolean poll()
    {
        if(request == null)
        {
            throw new IllegalStateException("Cannot poll(), request not open.");
        }

        try
        {
            return request.pollResponse();
        }
        catch(RuntimeException e)
        {
            closeRequest();
            state = FAILED;
            throw e;
        }
    }

    @Override
    public R await()
    {
        if(request == null)
        {
            throw new IllegalStateException("Cannot await(), request not open.");
        }

        try
        {
            request.awaitResponse();
            return get();
        }
        catch(RuntimeException e)
        {
            closeRequest();
            state = FAILED;
            throw e;
        }
    }

    @Override
    public R await(long timeout, TimeUnit timeUnit)
    {
        if(request == null)
        {
            throw new IllegalStateException("Cannot await(), request not open.");
        }

        try
        {
            request.awaitResponse(timeout, timeUnit);
            return get();
        }
        catch(RuntimeException e)
        {
            closeRequest();
            state = FAILED;
            throw e;
        }
    }

    public void reset()
    {
        if(state == RESPONSE_READ || state == FAILED)
        {
            request = null;
            responseValue = null;
            state = STATE_NEW;
        }
        else
        {
            throw new IllegalStateException("Cannot recycle command: request still open");
        }
    }

    protected R readReponse(DirectBuffer responseBuffer, int responseLength)
    {
        headerDecoder.wrap(responseBuffer, 0);

        final int schemaId = headerDecoder.schemaId();
        final int templateId = headerDecoder.templateId();
        final int blockLength = headerDecoder.blockLength();
        final int version = headerDecoder.version();

        if(responseSchemaId != schemaId || responseTemplateId != templateId)
        {
            throw new RuntimeException("Unexpected response in "+getClass()+": Got schemaId="+schemaId+", templateId="+templateId+ ". Expected schemaId="+responseSchemaId+" templateId="+responseTemplateId);
        }

        return readReponseBody(responseBuffer, headerDecoder.encodedLength(), blockLength, version);
    }

    protected abstract int getRequestLength();

    protected abstract void writeRequest(MutableDirectBuffer claimedRequestBuffer, int claimedOffset);

    protected abstract R readReponseBody(DirectBuffer responseBuffer, int offset, int actingBlockLength, int actingVersion);

}

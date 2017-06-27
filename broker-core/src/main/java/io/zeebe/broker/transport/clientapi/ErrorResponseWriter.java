package io.zeebe.broker.transport.clientapi;

import static io.zeebe.util.StringUtil.getBytes;
import static java.lang.String.format;

import java.nio.charset.StandardCharsets;

import io.zeebe.broker.Loggers;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.zeebe.protocol.clientapi.ErrorCode;
import io.zeebe.protocol.clientapi.ErrorResponseEncoder;
import io.zeebe.protocol.clientapi.MessageHeaderEncoder;
import io.zeebe.transport.ServerOutput;
import io.zeebe.transport.ServerResponse;
import io.zeebe.util.EnsureUtil;
import io.zeebe.util.buffer.BufferWriter;
import org.slf4j.Logger;

public class ErrorResponseWriter implements BufferWriter
{
    public static final Logger LOG = Loggers.TRANSPORT_LOGGER;

    protected final MessageHeaderEncoder messageHeaderEncoder = new MessageHeaderEncoder();
    protected final ErrorResponseEncoder errorResponseEncoder = new ErrorResponseEncoder();

    protected final DirectBuffer failedRequestBuffer = new UnsafeBuffer(0, 0);

    protected ErrorCode errorCode;
    protected byte[] errorMessage;

    protected final ServerOutput output;
    protected final ServerResponse response = new ServerResponse();

    public ErrorResponseWriter()
    {
        this(null);
    }

    public ErrorResponseWriter(ServerOutput output)
    {
        this.output = output;
    }

    public ErrorResponseWriter errorCode(ErrorCode errorCode)
    {
        this.errorCode = errorCode;
        return this;
    }

    public ErrorResponseWriter errorMessage(String errorMessage)
    {
        this.errorMessage = getBytes(errorMessage);
        return this;
    }

    public ErrorResponseWriter errorMessage(String errorMessage, Object... args)
    {
        this.errorMessage = getBytes(format(errorMessage, args));
        return this;
    }

    public ErrorResponseWriter failedRequest(DirectBuffer buffer, int offset, int length)
    {
        failedRequestBuffer.wrap(buffer, offset, length);
        return this;
    }


    @Override
    public void write(MutableDirectBuffer buffer, int offset)
    {
        // protocol header
        messageHeaderEncoder.wrap(buffer, offset);

        messageHeaderEncoder.blockLength(errorResponseEncoder.sbeBlockLength())
            .templateId(errorResponseEncoder.sbeTemplateId())
            .schemaId(errorResponseEncoder.sbeSchemaId())
            .version(errorResponseEncoder.sbeSchemaVersion());

        offset += messageHeaderEncoder.encodedLength();

        // error message
        errorResponseEncoder.wrap(buffer, offset);

        errorResponseEncoder
            .errorCode(errorCode)
            .putErrorData(errorMessage, 0, errorMessage.length)
            .putFailedRequest(failedRequestBuffer, 0, failedRequestBuffer.capacity());
    }

    public boolean tryWriteResponseOrLogFailure(ServerOutput output, int streamId, long requestId)
    {
        final boolean isWritten = tryWriteResponse(output, streamId, requestId);

        if (!isWritten)
        {
            LOG.error("Failed to write error response. Error code: '{}', error message: '{}'",
                errorCode.name(),
                new String(errorMessage, StandardCharsets.UTF_8)
            );
        }

        return isWritten;
    }


    public boolean tryWriteResponseOrLogFailure(int streamId, long requestId)
    {
        return tryWriteResponseOrLogFailure(this.output, streamId, requestId);
    }

    public boolean tryWriteResponse(ServerOutput output, int streamId, long requestId)
    {
        EnsureUtil.ensureNotNull("error code", errorCode);
        EnsureUtil.ensureNotNull("error message", errorMessage);

        try
        {
            response.reset()
                .remoteStreamId(streamId)
                .writer(this)
                .requestId(requestId);

            return output.sendResponse(response);
        }
        finally
        {
            reset();
        }
    }

    public boolean tryWriteResponse(int streamId, long requestId)
    {
        return tryWriteResponse(this.output, streamId, requestId);
    }

    @Override
    public int getLength()
    {
        return  MessageHeaderEncoder.ENCODED_LENGTH +
                ErrorResponseEncoder.BLOCK_LENGTH +
                ErrorResponseEncoder.errorDataHeaderLength() +
                errorMessage.length +
                ErrorResponseEncoder.failedRequestHeaderLength() +
                failedRequestBuffer.capacity();
    }

    protected void reset()
    {
        errorCode = null;
        errorMessage = null;

        failedRequestBuffer.wrap(0, 0);
    }

}

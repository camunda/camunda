package org.camunda.tngp.broker.transport.clientapi;

import static java.lang.String.format;
import static org.camunda.tngp.util.StringUtil.getBytes;

import java.nio.charset.StandardCharsets;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.broker.logstreams.BrokerEventMetadata;
import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.protocol.clientapi.ErrorCode;
import org.camunda.tngp.protocol.clientapi.ErrorResponseEncoder;
import org.camunda.tngp.protocol.clientapi.MessageHeaderEncoder;
import org.camunda.tngp.util.EnsureUtil;
import org.camunda.tngp.util.buffer.BufferWriter;

public class ErrorResponseWriter implements BufferWriter
{
    protected final MessageHeaderEncoder messageHeaderEncoder = new MessageHeaderEncoder();
    protected final ErrorResponseEncoder errorResponseEncoder = new ErrorResponseEncoder();

    protected final DirectBuffer failedRequestBuffer = new UnsafeBuffer(0, 0);

    protected ErrorCode errorCode;
    protected byte[] errorMessage;

    protected BrokerEventMetadata metadata;

    protected final ResponseWriter responseWriter;

    public ErrorResponseWriter(Dispatcher sendBuffer)
    {
        this.responseWriter = new ResponseWriter(sendBuffer);
    }

    public ErrorResponseWriter metadata(BrokerEventMetadata metadata)
    {
        this.metadata = metadata;
        return this;
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

    public boolean tryWriteResponse()
    {
        EnsureUtil.ensureNotNull("metadata", metadata);
        EnsureUtil.ensureNotNull("error code", errorCode);
        EnsureUtil.ensureNotNull("error message", errorMessage);

        try
        {
            return responseWriter.tryWrite(
                    metadata.getReqChannelId(),
                    metadata.getReqConnectionId(),
                    metadata.getReqRequestId(),
                    this);
        }
        finally
        {
            reset();
        }
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

    public boolean tryWriteResponseOrLogFailure()
    {
        final boolean isWritten = tryWriteResponse();

        if (!isWritten)
        {
            final  String failureMessage = String.format("Failed to write error response. Error code: '%s', error message: '%s'",
                    errorCode.name(),
                    new String(errorMessage, StandardCharsets.UTF_8));

            System.err.println(failureMessage);
        }
        return isWritten;
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
        metadata = null;

        errorCode = null;
        errorMessage = null;

        failedRequestBuffer.wrap(0, 0);
    }

}

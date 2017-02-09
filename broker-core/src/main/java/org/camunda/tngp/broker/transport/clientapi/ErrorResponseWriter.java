package org.camunda.tngp.broker.transport.clientapi;

import static org.camunda.tngp.dispatcher.impl.log.LogBufferAppender.RESULT_PADDING_AT_END_OF_PARTITION;

import java.nio.charset.StandardCharsets;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.broker.logstreams.BrokerEventMetadata;
import org.camunda.tngp.dispatcher.ClaimedFragment;
import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.protocol.clientapi.ErrorCode;
import org.camunda.tngp.protocol.clientapi.ErrorResponseEncoder;
import org.camunda.tngp.protocol.clientapi.MessageHeaderEncoder;
import org.camunda.tngp.transport.protocol.Protocols;
import org.camunda.tngp.transport.protocol.TransportHeaderDescriptor;
import org.camunda.tngp.transport.requestresponse.RequestResponseProtocolHeaderDescriptor;
import org.camunda.tngp.util.EnsureUtil;

public class ErrorResponseWriter
{
    protected final TransportHeaderDescriptor transportHeaderDescriptor = new TransportHeaderDescriptor();
    protected final RequestResponseProtocolHeaderDescriptor protocolHeaderDescriptor = new RequestResponseProtocolHeaderDescriptor();
    protected final MessageHeaderEncoder messageHeaderEncoder = new MessageHeaderEncoder();
    protected final ErrorResponseEncoder errorResponseEncoder = new ErrorResponseEncoder();

    protected final int headerSize =
            TransportHeaderDescriptor.HEADER_LENGTH +
            RequestResponseProtocolHeaderDescriptor.HEADER_LENGTH +
            MessageHeaderEncoder.ENCODED_LENGTH +
            ErrorResponseEncoder.BLOCK_LENGTH +
            ErrorResponseEncoder.errorDataHeaderLength() +
            ErrorResponseEncoder.failedRequestHeaderLength();

    protected final DirectBuffer failedRequestBuffer = new UnsafeBuffer(0, 0);

    protected final Dispatcher sendBuffer;
    protected final ClaimedFragment errorMessageBuffer = new ClaimedFragment();

    protected ErrorCode errorCode;
    protected String errorMessage;

    protected BrokerEventMetadata metadata;

    public ErrorResponseWriter(Dispatcher sendBuffer)
    {
        this.sendBuffer = sendBuffer;
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
        this.errorMessage = errorMessage;
        return this;
    }

    public ErrorResponseWriter errorMessage(String errorMessage, Object... args)
    {
        this.errorMessage = String.format(errorMessage, args);
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
        EnsureUtil.ensureNotNullOrEmpty("error message", errorMessage);

        final byte[] errorMessageBytes = errorMessage.getBytes(StandardCharsets.UTF_8);

        final int responseLength = headerSize + errorMessageBytes.length + failedRequestBuffer.capacity();

        long claimedOffset = -1;

        do
        {
            claimedOffset = sendBuffer.claim(errorMessageBuffer, responseLength, metadata.getReqChannelId());
        }
        while (claimedOffset == RESULT_PADDING_AT_END_OF_PARTITION);

        boolean isSent = false;

        if (claimedOffset >= 0)
        {
            try
            {
                writeResponseToFragment(errorMessageBytes);

                errorMessageBuffer.commit();
                isSent = true;
            }
            catch (RuntimeException e)
            {
                errorMessageBuffer.abort();
                throw e;
            }
            finally
            {
                reset();
            }
        }

        return isSent;
    }

    protected void writeResponseToFragment(final byte[] errorMessageBytes)
    {
        final MutableDirectBuffer buffer = errorMessageBuffer.getBuffer();
        int offset = errorMessageBuffer.getOffset();

        // transport protocol header
        transportHeaderDescriptor.wrap(buffer, offset)
            .protocolId(Protocols.REQUEST_RESPONSE);

        offset += TransportHeaderDescriptor.HEADER_LENGTH;

        // request/response protocol header
        protocolHeaderDescriptor.wrap(buffer, offset)
            .connectionId(metadata.getReqConnectionId())
            .requestId(metadata.getReqRequestId());

        offset += RequestResponseProtocolHeaderDescriptor.HEADER_LENGTH;

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
            .putErrorData(errorMessageBytes, 0, errorMessageBytes.length)
            .putFailedRequest(failedRequestBuffer, 0, failedRequestBuffer.capacity());
    }

    public boolean tryWriteResponseOrLogFailure()
    {
        final boolean isWritten = tryWriteResponse();

        if (!isWritten)
        {
            final  String failureMessage = String.format("Failed to write error response. Error code: '%s', error message: '%s'",
                    errorCode.name(),
                    errorMessage);

            System.err.println(failureMessage);
        }
        return isWritten;
    }

    protected void reset()
    {
        metadata = null;

        errorCode = null;
        errorMessage = null;

        failedRequestBuffer.wrap(0, 0);
    }

}

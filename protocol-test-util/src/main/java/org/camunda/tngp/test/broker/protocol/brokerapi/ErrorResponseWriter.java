package org.camunda.tngp.test.broker.protocol.brokerapi;

import java.nio.charset.StandardCharsets;

import org.agrona.MutableDirectBuffer;
import org.camunda.tngp.protocol.clientapi.ErrorCode;
import org.camunda.tngp.protocol.clientapi.ErrorResponseEncoder;
import org.camunda.tngp.protocol.clientapi.MessageHeaderEncoder;
import org.camunda.tngp.test.broker.protocol.MsgPackHelper;

public class ErrorResponseWriter<R> implements MessageBuilder<R>
{
    protected final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    protected final ErrorResponseEncoder bodyEncoder = new ErrorResponseEncoder();
    protected final MsgPackHelper msgPackHelper;

    protected ErrorCode errorCode;
    protected byte[] errorData;
    protected byte[] failedRequest;

    public ErrorResponseWriter(MsgPackHelper msgPackHelper)
    {
        this.msgPackHelper = msgPackHelper;
    }

    @Override
    public int getLength()
    {
        return MessageHeaderEncoder.ENCODED_LENGTH +
                ErrorResponseEncoder.BLOCK_LENGTH +
                ErrorResponseEncoder.errorDataHeaderLength() +
                errorData.length +
                ErrorResponseEncoder.failedRequestHeaderLength() +
                failedRequest.length;
    }

    @Override
    public void write(MutableDirectBuffer buffer, int offset)
    {
        // protocol header
        headerEncoder
            .wrap(buffer, offset)
            .blockLength(bodyEncoder.sbeBlockLength())
            .templateId(bodyEncoder.sbeTemplateId())
            .schemaId(bodyEncoder.sbeSchemaId())
            .version(bodyEncoder.sbeSchemaVersion());

        offset += headerEncoder.encodedLength();

        // protocol message
        bodyEncoder
            .wrap(buffer, offset)
            .errorCode(errorCode)
            .putErrorData(errorData, 0, errorData.length)
            .putFailedRequest(failedRequest, 0, failedRequest.length);

    }

    @Override
    public void initializeFrom(R context)
    {
        if (context instanceof ExecuteCommandRequest)
        {
            this.failedRequest = msgPackHelper.encodeAsMsgPack(((ExecuteCommandRequest) context).getCommand());
        }
        else if (context instanceof ControlMessageRequest)
        {
            this.failedRequest = msgPackHelper.encodeAsMsgPack(((ControlMessageRequest) context).getData());
        }
        else
        {
            throw new RuntimeException("Unexpected request type " + context.getClass().getName());
        }
    }

    public void setErrorCode(ErrorCode errorCode)
    {
        this.errorCode = errorCode;
    }

    public void setErrorData(String errorData)
    {
        this.errorData = errorData.getBytes(StandardCharsets.UTF_8);
    }
}

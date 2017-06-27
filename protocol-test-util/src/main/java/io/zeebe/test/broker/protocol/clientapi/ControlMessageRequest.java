package io.zeebe.test.broker.protocol.clientapi;

import java.util.Map;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

import io.zeebe.protocol.clientapi.ControlMessageRequestEncoder;
import io.zeebe.protocol.clientapi.ControlMessageType;
import io.zeebe.protocol.clientapi.MessageHeaderEncoder;
import io.zeebe.test.broker.protocol.MsgPackHelper;
import io.zeebe.transport.ClientOutput;
import io.zeebe.transport.ClientRequest;
import io.zeebe.transport.RemoteAddress;
import io.zeebe.util.buffer.BufferWriter;

public class ControlMessageRequest implements BufferWriter
{
    protected final MessageHeaderEncoder messageHeaderEncoder = new MessageHeaderEncoder();
    protected final ControlMessageRequestEncoder requestEncoder = new ControlMessageRequestEncoder();
    protected final MsgPackHelper msgPackHelper;
    protected final ClientOutput output;
    protected final RemoteAddress target;

    protected ControlMessageType messageType = ControlMessageType.NULL_VAL;
    protected byte[] encodedData;

    protected ClientRequest request;

    public ControlMessageRequest(ClientOutput output, RemoteAddress target, MsgPackHelper msgPackHelper)
    {
        this.output = output;
        this.target = target;
        this.msgPackHelper = msgPackHelper;
    }

    public ControlMessageRequest messageType(ControlMessageType messageType)
    {
        this.messageType = messageType;
        return this;
    }

    public ControlMessageRequest data(Map<String, Object> data)
    {
        this.encodedData = msgPackHelper.encodeAsMsgPack(data);
        return this;
    }

    public ControlMessageRequest send()
    {
        request = output.sendRequest(target, this);
        return this;
    }

    public ControlMessageResponse await()
    {
        final DirectBuffer responseBuffer = request.join();

        final ControlMessageResponse response = new ControlMessageResponse(msgPackHelper);
        response.wrap(responseBuffer, 0, responseBuffer.capacity());
        return response;
    }

    public ErrorResponse awaitError()
    {
        final DirectBuffer responseBuffer = request.join();

        final ErrorResponse errorResponse = new ErrorResponse(msgPackHelper);
        errorResponse.wrap(responseBuffer, 0, responseBuffer.capacity());
        return errorResponse;
    }

    @Override
    public int getLength()
    {
        return MessageHeaderEncoder.ENCODED_LENGTH +
                ControlMessageRequestEncoder.BLOCK_LENGTH +
                ControlMessageRequestEncoder.dataHeaderLength() +
                encodedData.length;
    }

    @Override
    public void write(MutableDirectBuffer buffer, int offset)
    {
        messageHeaderEncoder.wrap(buffer, offset)
            .schemaId(requestEncoder.sbeSchemaId())
            .templateId(requestEncoder.sbeTemplateId())
            .blockLength(requestEncoder.sbeBlockLength())
            .version(requestEncoder.sbeSchemaVersion());

        requestEncoder.wrap(buffer, offset + messageHeaderEncoder.encodedLength())
            .messageType(messageType)
            .putData(encodedData, 0, encodedData.length);
    }

}

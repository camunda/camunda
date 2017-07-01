package io.zeebe.test.broker.protocol.clientapi;

import java.util.Map;

import org.agrona.MutableDirectBuffer;
import io.zeebe.protocol.clientapi.ControlMessageRequestEncoder;
import io.zeebe.protocol.clientapi.ControlMessageType;
import io.zeebe.protocol.clientapi.MessageHeaderEncoder;
import io.zeebe.test.broker.protocol.MsgPackHelper;
import io.zeebe.transport.requestresponse.client.TransportConnectionPool;
import io.zeebe.util.buffer.BufferWriter;

public class ControlMessageRequest implements BufferWriter
{
    protected final MessageHeaderEncoder messageHeaderEncoder = new MessageHeaderEncoder();
    protected final ControlMessageRequestEncoder requestEncoder = new ControlMessageRequestEncoder();
    protected final MsgPackHelper msgPackHelper;

    protected ControlMessageType messageType = ControlMessageType.NULL_VAL;
    protected byte[] encodedData;

    protected final RequestResponseExchange requestResponseExchange;

    public ControlMessageRequest(TransportConnectionPool connectionPool, int channelId, MsgPackHelper msgPackHelper)
    {
        this.requestResponseExchange = new RequestResponseExchange(connectionPool, channelId);
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
        requestResponseExchange.sendRequest(this);
        return this;
    }

    public ControlMessageResponse await()
    {
        final ControlMessageResponse response = new ControlMessageResponse(msgPackHelper);
        requestResponseExchange.awaitResponse(response);
        return response;
    }

    public ErrorResponse awaitError()
    {
        final ErrorResponse errorResponse = new ErrorResponse(msgPackHelper);
        requestResponseExchange.awaitResponse(errorResponse);
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

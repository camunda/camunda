package org.camunda.tngp.test.broker.protocol.clientapi;

import java.util.Map;

import org.agrona.MutableDirectBuffer;
import org.camunda.tngp.protocol.clientapi.EventType;
import org.camunda.tngp.protocol.clientapi.ExecuteCommandRequestEncoder;
import org.camunda.tngp.protocol.clientapi.MessageHeaderEncoder;
import org.camunda.tngp.transport.requestresponse.client.TransportConnectionPool;
import org.camunda.tngp.util.buffer.BufferWriter;

public class ExecuteCommandRequest implements BufferWriter
{
    protected final MessageHeaderEncoder messageHeaderEncoder = new MessageHeaderEncoder();
    protected final ExecuteCommandRequestEncoder requestEncoder = new ExecuteCommandRequestEncoder();
    protected final MsgPackHelper msgPackHelper;

    protected int topicId = ExecuteCommandRequestEncoder.topicIdNullValue();
    protected long longKey = ExecuteCommandRequestEncoder.longKeyNullValue();
    protected EventType eventType = EventType.NULL_VAL;
    protected byte[] encodedCmd;

    protected final RequestResponseExchange requestResponseExchange;


    public ExecuteCommandRequest(TransportConnectionPool connectionPool, int channelId, MsgPackHelper msgPackHelper)
    {
        this.requestResponseExchange = new RequestResponseExchange(connectionPool, channelId);
        this.msgPackHelper = msgPackHelper;
    }

    public ExecuteCommandRequest topicId(int topicId)
    {
        this.topicId = topicId;
        return this;
    }

    public ExecuteCommandRequest eventType(EventType eventType)
    {
        this.eventType = eventType;
        return this;
    }

    public ExecuteCommandRequest command(Map<String, Object> command)
    {
        this.encodedCmd = msgPackHelper.encodeAsMsgPack(command);
        return this;
    }

    public ExecuteCommandRequest send()
    {
        requestResponseExchange.sendRequest(this);
        return this;
    }

    public ExecuteCommandResponse await()
    {
        final ExecuteCommandResponse response = new ExecuteCommandResponse(msgPackHelper);
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
                ExecuteCommandRequestEncoder.BLOCK_LENGTH +
                ExecuteCommandRequestEncoder.commandHeaderLength() +
                encodedCmd.length;
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
            .topicId(topicId)
            .longKey(longKey)
            .eventType(eventType)
            .putCommand(encodedCmd, 0, encodedCmd.length);
    }

}

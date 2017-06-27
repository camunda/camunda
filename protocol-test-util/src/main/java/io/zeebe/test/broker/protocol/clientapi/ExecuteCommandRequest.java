package io.zeebe.test.broker.protocol.clientapi;

import static io.zeebe.protocol.clientapi.ExecuteCommandRequestEncoder.keyNullValue;
import static io.zeebe.protocol.clientapi.ExecuteCommandRequestEncoder.partitionIdNullValue;
import static io.zeebe.util.StringUtil.getBytes;

import java.util.Map;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

import io.zeebe.protocol.clientapi.EventType;
import io.zeebe.protocol.clientapi.ExecuteCommandRequestEncoder;
import io.zeebe.protocol.clientapi.ExecuteCommandResponseEncoder;
import io.zeebe.protocol.clientapi.MessageHeaderEncoder;
import io.zeebe.test.broker.protocol.MsgPackHelper;
import io.zeebe.transport.ClientOutput;
import io.zeebe.transport.ClientRequest;
import io.zeebe.transport.RemoteAddress;
import io.zeebe.util.buffer.BufferWriter;

public class ExecuteCommandRequest implements BufferWriter
{
    protected final MessageHeaderEncoder messageHeaderEncoder = new MessageHeaderEncoder();
    protected final ExecuteCommandRequestEncoder requestEncoder = new ExecuteCommandRequestEncoder();
    protected final MsgPackHelper msgPackHelper;

    protected final ClientOutput output;
    protected final RemoteAddress target;

    protected String topicName;
    protected int partitionId = partitionIdNullValue();
    protected long key = keyNullValue();
    protected EventType eventType = EventType.NULL_VAL;
    protected byte[] encodedCmd;

    protected ClientRequest request;


    public ExecuteCommandRequest(ClientOutput output, RemoteAddress target, final MsgPackHelper msgPackHelper)
    {
        this.output = output;
        this.target = target;
        this.msgPackHelper = msgPackHelper;
    }

    public ExecuteCommandRequest topicName(final String topicName)
    {
        this.topicName = topicName;
        return this;
    }

    public ExecuteCommandRequest partitionId(final int partitionId)
    {
        this.partitionId = partitionId;
        return this;
    }

    public ExecuteCommandRequest key(final long key)
    {
        this.key = key;
        return this;
    }

    public ExecuteCommandRequest eventType(final EventType eventType)
    {
        this.eventType = eventType;
        return this;
    }

    public ExecuteCommandRequest command(final Map<String, Object> command)
    {
        this.encodedCmd = msgPackHelper.encodeAsMsgPack(command);
        return this;
    }

    public ExecuteCommandRequest send()
    {
        request = output.sendRequest(target, this);
        return this;
    }

    public ExecuteCommandResponse await()
    {
        final DirectBuffer responseBuffer = request.join();
        request.close();

        final ExecuteCommandResponse response = new ExecuteCommandResponse(msgPackHelper);
        response.wrap(responseBuffer, 0, responseBuffer.capacity());
        return response;
    }

    public ErrorResponse awaitError()
    {
        final DirectBuffer responseBuffer = request.join();
        request.close();

        final ErrorResponse errorResponse = new ErrorResponse(msgPackHelper);
        errorResponse.wrap(responseBuffer, 0, responseBuffer.capacity());
        return errorResponse;
    }

    @Override
    public int getLength()
    {
        return MessageHeaderEncoder.ENCODED_LENGTH +
                ExecuteCommandRequestEncoder.BLOCK_LENGTH +
                ExecuteCommandResponseEncoder.topicNameHeaderLength() +
                getBytes(topicName).length +
                ExecuteCommandRequestEncoder.commandHeaderLength() +
                encodedCmd.length;
    }

    @Override
    public void write(final MutableDirectBuffer buffer, final int offset)
    {
        messageHeaderEncoder.wrap(buffer, offset)
            .schemaId(requestEncoder.sbeSchemaId())
            .templateId(requestEncoder.sbeTemplateId())
            .blockLength(requestEncoder.sbeBlockLength())
            .version(requestEncoder.sbeSchemaVersion());

        requestEncoder.wrap(buffer, offset + messageHeaderEncoder.encodedLength())
            .partitionId(partitionId)
            .key(key)
            .eventType(eventType)
            .topicName(topicName)
            .putCommand(encodedCmd, 0, encodedCmd.length);
    }

}

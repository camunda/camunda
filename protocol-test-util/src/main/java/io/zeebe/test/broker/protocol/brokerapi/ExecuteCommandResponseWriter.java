package io.zeebe.test.broker.protocol.brokerapi;

import static io.zeebe.protocol.clientapi.ExecuteCommandResponseEncoder.eventHeaderLength;
import static io.zeebe.protocol.clientapi.ExecuteCommandResponseEncoder.topicNameHeaderLength;
import static io.zeebe.util.StringUtil.getBytes;

import java.util.Map;
import java.util.function.Function;

import org.agrona.MutableDirectBuffer;
import io.zeebe.protocol.clientapi.ExecuteCommandResponseEncoder;
import io.zeebe.protocol.clientapi.MessageHeaderEncoder;
import io.zeebe.test.broker.protocol.MsgPackHelper;

public class ExecuteCommandResponseWriter implements MessageBuilder<ExecuteCommandRequest>
{
    protected final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    protected final ExecuteCommandResponseEncoder bodyEncoder = new ExecuteCommandResponseEncoder();
    protected final MsgPackHelper msgPackHelper;

    protected Function<ExecuteCommandRequest, Long> keyFunction = r -> r.key();
    protected Function<ExecuteCommandRequest, String> topicNameFunction = r -> r.topicName();
    protected Function<ExecuteCommandRequest, Integer> partitionIdFunction = r -> r.partitionId();
    protected Function<ExecuteCommandRequest, Map<String, Object>> eventFunction;

    protected long key;
    protected String topicName;
    protected int partitionId;
    protected byte[] event;

    public ExecuteCommandResponseWriter(MsgPackHelper msgPackHelper)
    {
        this.msgPackHelper = msgPackHelper;
    }

    @Override
    public void initializeFrom(ExecuteCommandRequest request)
    {
        key = keyFunction.apply(request);
        topicName = topicNameFunction.apply(request);
        partitionId = partitionIdFunction.apply(request);
        final Map<String, Object> deserializedEvent = eventFunction.apply(request);
        event = msgPackHelper.encodeAsMsgPack(deserializedEvent);
    }

    public void setTopicNameFunction(final Function<ExecuteCommandRequest, String> topicNameFunction)
    {
        this.topicNameFunction = topicNameFunction;
    }

    public void setPartitionIdFunction(Function<ExecuteCommandRequest, Integer> partitionIdFunction)
    {
        this.partitionIdFunction = partitionIdFunction;
    }

    public void setEventFunction(Function<ExecuteCommandRequest, Map<String, Object>> eventFunction)
    {
        this.eventFunction = eventFunction;
    }

    public void setKeyFunction(Function<ExecuteCommandRequest, Long> keyFunction)
    {
        this.keyFunction = keyFunction;
    }

    @Override
    public int getLength()
    {
        return MessageHeaderEncoder.ENCODED_LENGTH +
                ExecuteCommandResponseEncoder.BLOCK_LENGTH +
                topicNameHeaderLength() +
                getBytes(topicName).length +
                eventHeaderLength() +
                event.length;
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
            .partitionId(partitionId)
            .key(key)
            .topicName(topicName)
            .putEvent(event, 0, event.length);

    }

}

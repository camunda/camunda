package org.camunda.tngp.test.broker.protocol.brokerapi;

import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

import org.agrona.MutableDirectBuffer;
import org.camunda.tngp.protocol.clientapi.ExecuteCommandResponseEncoder;
import org.camunda.tngp.protocol.clientapi.MessageHeaderEncoder;
import org.camunda.tngp.test.broker.protocol.MsgPackHelper;

public class ExecuteCommandResponseStub implements ResponseStub<ExecuteCommandRequest>
{

    protected final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    protected final ExecuteCommandResponseEncoder bodyEncoder = new ExecuteCommandResponseEncoder();
    protected final MsgPackHelper msgPackHelper;

    protected Predicate<ExecuteCommandRequest> activationFunction;
    protected Function<ExecuteCommandRequest, Long> keyFunction;
    protected Function<ExecuteCommandRequest, Integer> topicIdFunction;
    protected Function<ExecuteCommandRequest, Map<String, Object>> eventFunction;

    protected long key;
    protected int topicId;
    protected byte[] event;

    public ExecuteCommandResponseStub(MsgPackHelper msgPackHelper, Predicate<ExecuteCommandRequest> activationFunction)
    {
        this.msgPackHelper = msgPackHelper;
        this.activationFunction = activationFunction;
    }

    public boolean applies(ExecuteCommandRequest request)
    {
        return activationFunction.test(request);
    }

    public void initiateFrom(ExecuteCommandRequest request)
    {
        key = keyFunction.apply(request);
        topicId = topicIdFunction.apply(request);
        final Map<String, Object> deserializedEvent = eventFunction.apply(request);
        event = msgPackHelper.encodeAsMsgPack(deserializedEvent);
    }

    public void setTopicIdFunction(Function<ExecuteCommandRequest, Integer> topicIdFunction)
    {
        this.topicIdFunction = topicIdFunction;
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
                ExecuteCommandResponseEncoder.eventHeaderLength() +
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
            .topicId(topicId)
            .key(key)
            .putEvent(event, 0, event.length);

    }
}

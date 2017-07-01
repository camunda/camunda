package io.zeebe.test.broker.protocol.brokerapi;

import java.util.Map;
import java.util.function.Function;

import org.agrona.MutableDirectBuffer;
import io.zeebe.protocol.clientapi.ControlMessageResponseEncoder;
import io.zeebe.protocol.clientapi.MessageHeaderEncoder;
import io.zeebe.test.broker.protocol.MsgPackHelper;

public class ControlMessageResponseWriter implements MessageBuilder<ControlMessageRequest>
{

    protected final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    protected final ControlMessageResponseEncoder bodyEncoder = new ControlMessageResponseEncoder();
    protected final MsgPackHelper msgPackHelper;

    protected Function<ControlMessageRequest, Map<String, Object>> dataFunction;

    protected byte[] data;

    public ControlMessageResponseWriter(MsgPackHelper msgPackHelper)
    {
        this.msgPackHelper = msgPackHelper;
    }

    public void setDataFunction(Function<ControlMessageRequest, Map<String, Object>> dataFunction)
    {
        this.dataFunction = dataFunction;
    }

    @Override
    public int getLength()
    {
        return MessageHeaderEncoder.ENCODED_LENGTH +
                ControlMessageResponseEncoder.BLOCK_LENGTH +
                ControlMessageResponseEncoder.dataHeaderLength() +
                data.length;
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
            .putData(data, 0, data.length);

    }

    @Override
    public void initializeFrom(ControlMessageRequest context)
    {
        final Map<String, Object> deserializedData = dataFunction.apply(context);
        data = msgPackHelper.encodeAsMsgPack(deserializedData);
    }
}
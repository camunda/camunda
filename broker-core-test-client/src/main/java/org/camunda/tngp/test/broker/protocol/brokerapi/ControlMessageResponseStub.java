package org.camunda.tngp.test.broker.protocol.brokerapi;

import java.util.Map;
import java.util.function.Function;

import org.agrona.MutableDirectBuffer;
import org.camunda.tngp.protocol.clientapi.ControlMessageResponseEncoder;
import org.camunda.tngp.protocol.clientapi.MessageHeaderEncoder;

public class ControlMessageResponseStub implements ResponseStub<ControlMessageRequest>
{

    protected final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    protected final ControlMessageResponseEncoder bodyEncoder = new ControlMessageResponseEncoder();
    protected final MsgPackHelper msgPackHelper;

    protected Function<ControlMessageRequest, Boolean> activationFunction;
    protected Function<ControlMessageRequest, Map<String, Object>> dataFunction;

    protected byte[] data;

    public ControlMessageResponseStub(MsgPackHelper msgPackHelper, Function<ControlMessageRequest, Boolean> activationFunction)
    {
        this.msgPackHelper = msgPackHelper;
        this.activationFunction = activationFunction;
    }

    public boolean applies(ControlMessageRequest request)
    {
        return activationFunction.apply(request);
    }

    public void initiateFrom(ControlMessageRequest request)
    {
        final Map<String, Object> deserializedData = dataFunction.apply(request);
        data = msgPackHelper.encodeAsMsgPack(deserializedData);
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
}
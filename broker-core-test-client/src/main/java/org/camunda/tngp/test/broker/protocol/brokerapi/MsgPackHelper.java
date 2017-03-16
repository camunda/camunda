package org.camunda.tngp.test.broker.protocol.brokerapi;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Map;

import org.msgpack.jackson.dataformat.MessagePackFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

public class MsgPackHelper
{
    protected ObjectMapper objectMapper;

    public MsgPackHelper()
    {
        this.objectMapper = new ObjectMapper(new MessagePackFactory());
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> readMsgPack(InputStream is)
    {
        final ObjectMapper objectMapper = new ObjectMapper(new MessagePackFactory());
        try
        {
            return objectMapper.readValue(is, Map.class);
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    public byte[] encodeAsMsgPack(Object command)
    {
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        final ObjectMapper objectMapper = new ObjectMapper(new MessagePackFactory());
        try
        {
            objectMapper.writer().writeValue(byteArrayOutputStream, command);
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }

        return byteArrayOutputStream.toByteArray();
    }

}

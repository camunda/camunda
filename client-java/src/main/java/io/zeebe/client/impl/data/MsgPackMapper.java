package io.zeebe.client.impl.data;

import com.fasterxml.jackson.databind.ObjectMapper;

public class MsgPackMapper
{

    protected final ObjectMapper objectMapper;

    public MsgPackMapper(ObjectMapper objectMapper)
    {
        this.objectMapper = objectMapper;
    }

    public <T> T convert(byte[] msgPack, Class<T> targetClass)
    {
        try
        {
            return objectMapper.readValue(msgPack, targetClass);
        }
        catch (Exception e)
        {
            throw new RuntimeException("Could not convert msgpack to object of type " + targetClass.getName(), e);
        }
    }


}

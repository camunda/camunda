package org.camunda.tngp.client.benchmark.msgpack;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.io.DirectBufferInputStream;
import org.agrona.io.DirectBufferOutputStream;
import org.msgpack.jackson.dataformat.MessagePackFactory;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;

public class MsgPackJacksonSerializer implements MsgPackSerializer
{

    protected ObjectMapper objectMapper;
    protected DirectBufferInputStream inStream = new DirectBufferInputStream();
    protected DirectBufferOutputStream outStream = new DirectBufferOutputStream();

    protected JavaType eventType;
    protected ObjectWriter eventTypeWriter;
    protected ObjectReader eventTypeReader;


    public MsgPackJacksonSerializer()
    {
        this.objectMapper = new ObjectMapper(new MessagePackFactory());

        // optimization to avoid duplicate class scanning;
        eventType = objectMapper.getTypeFactory().constructSimpleType(JacksonTaskEvent.class, null);
        eventTypeWriter = objectMapper.writerFor(eventType);
        eventTypeReader = objectMapper.readerFor(eventType);
    }

    @Override
    public void serialize(Object value, MutableDirectBuffer buf, int offset) throws Exception
    {
        outStream.wrap(buf, offset, buf.capacity() - offset);
        eventTypeWriter.writeValue(outStream, value);
    }

    @Override
    public Object deserialize(Class<?> clazz, DirectBuffer buf, int offset, int length) throws Exception
    {
        inStream.wrap(buf, offset, length);
        return eventTypeReader.readValue(inStream);
    }

    @Override
    public String getDescription()
    {
        return "Jackson";
    }
}

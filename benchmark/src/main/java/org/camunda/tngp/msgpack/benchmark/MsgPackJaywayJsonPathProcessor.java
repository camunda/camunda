package org.camunda.tngp.msgpack.benchmark;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import org.msgpack.jackson.dataformat.MessagePackFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.InvalidJsonException;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.spi.json.JacksonJsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;

public class MsgPackJaywayJsonPathProcessor implements JsonPathProcessor
{

    protected Configuration jacksonConfig;

    public MsgPackJaywayJsonPathProcessor()
    {
        ObjectMapper objectMapper = new ObjectMapper(new MessagePackFactory());
        jacksonConfig = Configuration.defaultConfiguration()
                .mappingProvider(new JacksonMappingProvider(objectMapper))
                .jsonProvider(new CustomJacksonJsonProvider(objectMapper))
                ;
    }

    @Override
    public String evaluateJsonPath(byte[] msgPack, String jsonPath) throws Exception
    {
        ByteArrayInputStream inputStream = new ByteArrayInputStream(msgPack);
        JsonPath query = JsonPath.compile(jsonPath);
        Object result = query.read(inputStream, jacksonConfig);
        return result.toString();
    }

    protected static class CustomJacksonJsonProvider extends JacksonJsonProvider
    {
        public CustomJacksonJsonProvider(ObjectMapper o)
        {
            super(o);
        }

        @Override
        public Object parse(InputStream jsonStream, String charset) throws InvalidJsonException
        {
            try
            {
                return objectReader.readValue(jsonStream);
            } catch (Exception e)
            {
                throw new InvalidJsonException(e);
            }
        }
    }

}

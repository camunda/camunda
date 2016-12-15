package org.camunda.tngp.msgpack.benchmark;

import java.io.ByteArrayInputStream;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.spi.json.JacksonJsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;

public class JaywayJsonPathProcessor implements JsonPathProcessor
{

    protected Configuration jacksonConfig;

    public JaywayJsonPathProcessor()
    {
        jacksonConfig = Configuration.defaultConfiguration()
                .mappingProvider(new JacksonMappingProvider())
                .jsonProvider(new JacksonJsonProvider())
                ;
    }

    @Override
    public String evaluateJsonPath(byte[] json, String jsonPath) throws Exception
    {
        ByteArrayInputStream inputStream = new ByteArrayInputStream(json);
        JsonPath query = JsonPath.compile(jsonPath);
        Object result = query.read(inputStream, jacksonConfig);
        return result.toString();
    }

}

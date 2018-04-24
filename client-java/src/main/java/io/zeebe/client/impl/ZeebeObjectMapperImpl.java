/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.client.impl;

import java.io.*;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import io.zeebe.client.api.record.Record;
import io.zeebe.client.api.record.ZeebeObjectMapper;
import io.zeebe.client.impl.data.MsgPackConverter;
import org.msgpack.jackson.dataformat.MessagePackFactory;

public class ZeebeObjectMapperImpl implements ZeebeObjectMapper
{
    private final ObjectMapper objectMapper;
    private final InjectableValues.Std injectableValues;

    public ZeebeObjectMapperImpl(MsgPackConverter msgPackConverter)
    {
        objectMapper = new ObjectMapper(new MessagePackFactory().setReuseResourceInGenerator(false).setReuseResourceInParser(false));

        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        this.injectableValues = new InjectableValues.Std();

        injectableValues.addValue(MsgPackConverter.class, msgPackConverter);
        injectableValues.addValue(ZeebeObjectMapper.class, this);

        objectMapper.setInjectableValues(injectableValues);
    }

    @Override
    public String toJson(Record record)
    {
        try
        {
            // TODO Use {@link #writeValueAsBytes(Object)} instead
            return objectMapper.writeValueAsString(record);
        }
        catch (JsonProcessingException e)
        {
            throw new RuntimeException(String.format("Failed to serialize object '%s' to JSON", record), e);
        }
    }

    @Override
    public <T extends Record> T fromJson(String json, Class<T> recordClass)
    {
        try
        {
            return objectMapper.readValue(json, recordClass);
        }
        catch (IOException e)
        {
            throw new RuntimeException(String.format("Failed deserialize JSON '%s' to object of type '%s'", json, recordClass), e);
        }
    }

    public <T extends Record> T fromJson(byte[] json, Class<T> recordClass)
    {
        try
        {
            return objectMapper.readValue(json, recordClass);
        }
        catch (IOException e)
        {
            throw new RuntimeException(String.format("Failed deserialize JSON byte array to object of type '%s'", recordClass), e);
        }
    }

    public void toJson(OutputStream outputStream, Object value)
    {
        try
        {
            objectMapper.writeValue(outputStream, value);
        }
        catch (IOException e)
        {
            throw new RuntimeException(String.format("Failed to serialize object '%s' to JSON", value), e);
        }
    }

    public <T> T fromJson(InputStream inputStream, Class<T> valueType)
    {
        try
        {
            return objectMapper.readValue(inputStream, valueType);
        }
        catch (IOException e)
        {
            throw new RuntimeException(String.format("Failed deserialize JSON stream to object of type '%s'", valueType), e);
        }
    }

}

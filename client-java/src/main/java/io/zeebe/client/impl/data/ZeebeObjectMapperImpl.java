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
package io.zeebe.client.impl.data;

import java.io.*;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.zeebe.client.api.record.*;
import io.zeebe.client.cmd.ClientException;
import io.zeebe.client.impl.record.*;
import org.msgpack.jackson.dataformat.MessagePackFactory;

public class ZeebeObjectMapperImpl implements ZeebeObjectMapper
{
    private static final TypeReference<Map<String, Object>> MAP_TYPE_REFERENCE = new TypeReference<Map<String, Object>>()
    { };

    private final ObjectMapper msgpackObjectMapper;
    private final ObjectMapper jsonObjectMapper;

    private final MsgPackConverter msgPackConverter;

    public ZeebeObjectMapperImpl()
    {
        this.msgPackConverter = new MsgPackConverter();

        final InjectableValues.Std injectableValues = new InjectableValues.Std();
        injectableValues.addValue(ZeebeObjectMapperImpl.class, this);

        msgpackObjectMapper = createMsgpackObjectMapper(injectableValues);
        jsonObjectMapper = createDefaultObjectMapper(injectableValues);
    }

    private ObjectMapper createDefaultObjectMapper(InjectableValues injectableValues)
    {
        final ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        objectMapper.setInjectableValues(injectableValues);

        objectMapper.registerModule(new JavaTimeModule()); // to serialize INSTANT
        objectMapper.registerModule(new JsonPayloadModule(this));

        return objectMapper;
    }

    private ObjectMapper createMsgpackObjectMapper(InjectableValues injectableValues)
    {
        final MessagePackFactory msgpackFactory = new MessagePackFactory().setReuseResourceInGenerator(false).setReuseResourceInParser(false);
        final ObjectMapper objectMapper = new ObjectMapper(msgpackFactory);

        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        objectMapper.addMixIn(RecordImpl.class, MsgpackRecordMixin.class);

        objectMapper.setInjectableValues(injectableValues);

        objectMapper.registerModule(new MsgpackInstantModule());
        objectMapper.registerModule(new MsgpackPayloadModule(this));

        return objectMapper;
    }

    @Override
    public String toJson(Record record)
    {
        try
        {
            return jsonObjectMapper.writeValueAsString(record);
        }
        catch (JsonProcessingException e)
        {
            throw new ClientException(String.format("Failed to serialize object '%s' to JSON", record), e);
        }
    }

    @Override
    public <T extends Record> T fromJson(String json, Class<T> recordClass)
    {
        final Class<T> implClass = RecordClassMapping.getRecordImplClass(recordClass);
        if (implClass == null)
        {
            throw new ClientException(String.format("Cannot deserialize JSON: unknown record class '%s'", recordClass.getName()));
        }

        try
        {
            final T record = jsonObjectMapper.readValue(json, implClass);

            // verify that the record is de-serialized into the correct type
            final RecordMetadata metadata = record.getMetadata();
            final Class<RecordImpl> recordImplClass = RecordClassMapping.getRecordImplClass(metadata.getRecordType(), metadata.getValueType());
            if (!implClass.equals(recordImplClass))
            {
                throw new ClientException(
                        String.format("Cannot deserialize JSON to object of type '%s'. Incompatible type for record '%s - %s'.",
                                      recordClass.getName(),
                                      metadata.getRecordType(),
                                      metadata.getValueType()));
            }

            return record;
        }
        catch (IOException e)
        {
            throw new ClientException(String.format("Failed deserialize JSON '%s' to object of type '%s'", json, recordClass.getName()), e);
        }
    }

    public <T extends Record> T asRecordType(UntypedRecordImpl record, Class<T> recordClass)
    {
        try
        {
            return msgpackObjectMapper.readValue(record.getAsMsgPack(), recordClass);
        }
        catch (IOException e)
        {
            throw new RuntimeException(String.format("Failed deserialize JSON to object of type '%s'", recordClass), e);
        }
    }

    public void toMsgpack(OutputStream outputStream, Object value)
    {
        try
        {
            msgpackObjectMapper.writeValue(outputStream, value);
        }
        catch (IOException e)
        {
            throw new RuntimeException(String.format("Failed to serialize object '%s' to Msgpack JSON", value), e);
        }
    }

    public byte[] toMsgpack(Object value)
    {
        try
        {
            return msgpackObjectMapper.writeValueAsBytes(value);
        }
        catch (IOException e)
        {
            throw new ClientException(String.format("Failed to serialize object '%s' to Msgpack JSON", value), e);
        }
    }

    public <T> T fromMsgpack(InputStream inputStream, Class<T> valueType)
    {
        try
        {
            return msgpackObjectMapper.readValue(inputStream, valueType);
        }
        catch (IOException e)
        {
            throw new RuntimeException(String.format("Failed deserialize Msgpack JSON stream to object of type '%s'", valueType), e);
        }
    }

    public Map<String, Object> fromMsgpackAsMap(byte[] msgpack)
    {
        try
        {
            return msgpackObjectMapper.readValue(msgpack, MAP_TYPE_REFERENCE);
        }
        catch (IOException e)
        {
            throw new RuntimeException("Failed deserialize Msgpack JSON to map", e);
        }
    }

    public <T> T fromMsgpackAsType(byte[] msgpack, Class<T> type)
    {
        try
        {
            return msgpackObjectMapper.readValue(msgpack, type);
        }
        catch (IOException e)
        {
            throw new RuntimeException(String.format("Failed deserialize Msgpack JSON to type '%s'", type.getName()), e);
        }
    }

    public MsgPackConverter getMsgPackConverter()
    {
        return msgPackConverter;
    }

    abstract class MsgpackRecordMixin
    {
        // records from broker does't have metadata inside (instead it's part of SBE layer)
        @JsonIgnore
        abstract RecordMetadataImpl getMetadata();
    }

}

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
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.zeebe.client.api.commands.*;
import io.zeebe.client.api.events.*;
import io.zeebe.client.api.record.Record;
import io.zeebe.client.api.record.ZeebeObjectMapper;
import io.zeebe.client.impl.command.*;
import io.zeebe.client.impl.event.*;
import io.zeebe.client.impl.record.*;
import org.msgpack.jackson.dataformat.MessagePackFactory;

public class ZeebeObjectMapperImpl implements ZeebeObjectMapper
{
    private final ObjectMapper msgpackObjectMapper;
    private final ObjectMapper jsonObjectMapper;

    private static final Map<Class<?>, Class<?>> RECORD_IMPL_CLASS_MAPPING;

    static
    {
        RECORD_IMPL_CLASS_MAPPING = new HashMap<>();
        RECORD_IMPL_CLASS_MAPPING.put(JobEvent.class, JobEventImpl.class);
        RECORD_IMPL_CLASS_MAPPING.put(JobCommand.class, JobCommandImpl.class);
        RECORD_IMPL_CLASS_MAPPING.put(WorkflowInstanceEvent.class, WorkflowInstanceEventImpl.class);
        RECORD_IMPL_CLASS_MAPPING.put(WorkflowInstanceCommand.class, WorkflowInstanceCommandImpl.class);
        RECORD_IMPL_CLASS_MAPPING.put(IncidentEvent.class, IncidentEventImpl.class);
        RECORD_IMPL_CLASS_MAPPING.put(IncidentCommand.class, IncidentCommandImpl.class);
        RECORD_IMPL_CLASS_MAPPING.put(RaftEvent.class, RaftEventImpl.class);
        RECORD_IMPL_CLASS_MAPPING.put(DeploymentEvent.class, DeploymentEventImpl.class);
        RECORD_IMPL_CLASS_MAPPING.put(DeploymentCommand.class, DeploymentCommandImpl.class);
    }

    public ZeebeObjectMapperImpl(MsgPackConverter msgPackConverter)
    {
        final InjectableValues.Std injectableValues = new InjectableValues.Std();
        injectableValues.addValue(MsgPackConverter.class, msgPackConverter);
        injectableValues.addValue(ZeebeObjectMapperImpl.class, this);

        msgpackObjectMapper = createMsgpackObjectMapper(msgPackConverter, injectableValues);
        jsonObjectMapper = createDefaultObjectMapper(msgPackConverter, injectableValues);
    }

    private ObjectMapper createDefaultObjectMapper(MsgPackConverter msgPackConverter, InjectableValues injectableValues)
    {
        final ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        objectMapper.setInjectableValues(injectableValues);

        objectMapper.registerModule(new JavaTimeModule()); // to serialize INSTANT
        objectMapper.registerModule(new JsonPayloadModule(msgPackConverter));

        return objectMapper;
    }

    private ObjectMapper createMsgpackObjectMapper(MsgPackConverter msgPackConverter, InjectableValues injectableValues)
    {
        final MessagePackFactory msgpackFactory = new MessagePackFactory().setReuseResourceInGenerator(false).setReuseResourceInParser(false);
        final ObjectMapper objectMapper = new ObjectMapper(msgpackFactory);

        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        objectMapper.addMixIn(RecordImpl.class, MsgpackRecordMixin.class);

        objectMapper.setInjectableValues(injectableValues);

        objectMapper.registerModule(new MsgpackInstantModule());
        objectMapper.registerModule(new MsgpackPayloadModule(msgPackConverter));

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
            throw new RuntimeException(String.format("Failed to serialize object '%s' to JSON", record), e);
        }
    }

    @Override
    public <T extends Record> T fromJson(String json, Class<T> recordClass)
    {
        final Class<T> implClass = getRecordImplClass(recordClass);

        try
        {
            return jsonObjectMapper.readValue(json, implClass);
        }
        catch (IOException e)
        {
            throw new RuntimeException(String.format("Failed deserialize JSON '%s' to object of type '%s'", json, recordClass.getName()), e);
        }
    }

    @SuppressWarnings("unchecked")
    private <T extends Record> Class<T> getRecordImplClass(Class<T> recordClass)
    {
        if (RECORD_IMPL_CLASS_MAPPING.containsKey(recordClass))
        {
            return (Class<T>) RECORD_IMPL_CLASS_MAPPING.get(recordClass);
        }
        else if (RECORD_IMPL_CLASS_MAPPING.containsValue(recordClass))
        {
            return recordClass;
        }
        else
        {
            throw new RuntimeException(String.format("Cannot deserialize JSON: unknown record class '%s'", recordClass.getName()));
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

    abstract class MsgpackRecordMixin
    {
        // records from broker does't have metadata inside (instead it's part of SBE layer)
        @JsonIgnore
        abstract RecordMetadataImpl getMetadata();
    }

}

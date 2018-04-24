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
package io.zeebe.client.impl.record;

import java.io.InputStream;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.zeebe.client.api.record.JobRecord;
import io.zeebe.client.api.record.ZeebeObjectMapper;
import io.zeebe.client.impl.data.MsgPackConverter;
import io.zeebe.client.impl.event.JobEventImpl;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.intent.JobIntent;

public abstract class JobRecordImpl extends RecordImpl implements JobRecord
{
    private Map<String, Object> headers = new HashMap<>();
    private Map<String, Object> customHeaders = new HashMap<>();

    private long lockTime = Protocol.INSTANT_NULL_VALUE;
    private String lockOwner;
    private Integer retries;
    private String type;
    private final MsgPackField payload;

    public JobRecordImpl(ZeebeObjectMapper objectMapper, MsgPackConverter msgPackConverter, RecordType recordType)
    {
        super(objectMapper, recordType, ValueType.JOB);

        this.payload = new MsgPackField(msgPackConverter);
    }

    public JobRecordImpl(JobRecordImpl base, JobIntent intent)
    {
        super(base, intent);

        this.headers = new HashMap<>(base.headers);
        this.customHeaders = new HashMap<>(base.customHeaders);
        this.lockTime = base.lockTime;
        this.lockOwner = base.lockOwner;
        this.retries = base.retries;
        this.type = base.type;
        this.payload = new MsgPackField(base.payload);
    }

    @Override
    public String getType()
    {
        return type;
    }

    public void setType(String type)
    {
        this.type = type;
    }

    @Override
    @JsonIgnore
    public Instant getLockExpirationTime()
    {
        if (lockTime == Protocol.INSTANT_NULL_VALUE)
        {
            return null;
        }
        else
        {
            return Instant.ofEpochMilli(lockTime);
        }
    }

    public long getLockTime()
    {
        return lockTime;
    }

    public void setLockTime(long lockTime)
    {
        this.lockTime = lockTime;
    }

    @Override
    public Map<String, Object> getHeaders()
    {
        return headers;
    }

    public void setHeaders(Map<String, Object> headers)
    {
        this.headers.clear();
        this.headers.putAll(headers);
    }

    @Override
    public Map<String, Object> getCustomHeaders()
    {
        return customHeaders;
    }

    public void setCustomHeaders(Map<String, Object> customHeaders)
    {
        this.customHeaders.clear();
        this.customHeaders.putAll(customHeaders);
    }

    @Override
    public String getLockOwner()
    {
        return lockOwner;
    }

    public void setLockOwner(String lockOwner)
    {
        this.lockOwner = lockOwner;
    }

    @Override
    @JsonIgnore
    public String getPayload()
    {
        return payload.getAsJson();
    }

    @JsonProperty("payload")
    public byte[] getPayloadMsgPack()
    {
        return payload.getMsgPack();
    }

    @JsonProperty("payload")
    public void setPayload(byte[] msgPack)
    {
        this.payload.setMsgPack(msgPack);
    }

    public void setPayload(String json)
    {
        this.payload.setJson(json);
    }

    public void setPayload(InputStream jsonStream)
    {
        this.payload.setJson(jsonStream);
    }

    @Override
    public Integer getRetries()
    {
        return retries;
    }

    public void setRetries(Integer retries)
    {
        this.retries = retries;
    }

    @Override
    public Class<? extends RecordImpl> getEventClass()
    {
        return JobEventImpl.class;
    }

}

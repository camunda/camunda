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

import java.io.InputStream;
import java.util.Map;

import io.zeebe.client.cmd.ClientException;

public class PayloadField
{
    private final ZeebeObjectMapperImpl objectMapper;
    private final MsgPackConverter msgPackConverter;

    private byte[] msgPack;

    public PayloadField(ZeebeObjectMapperImpl objectMapper)
    {
        this.objectMapper = objectMapper;
        this.msgPackConverter = objectMapper.getMsgPackConverter();
    }

    public PayloadField(PayloadField other)
    {
        this.objectMapper = other.objectMapper;
        this.msgPackConverter = other.msgPackConverter;
        this.msgPack = other.msgPack;
    }

    public byte[] getMsgPack()
    {
        return msgPack;
    }

    public void setMsgPack(byte[] msgPack)
    {
        this.msgPack = msgPack;
    }

    public String getAsJsonString()
    {
        if (msgPack != null)
        {
            return msgPackConverter.convertToJson(msgPack);
        }
        else
        {
            return null;
        }
    }

    public Map<String, Object> getAsMap()
    {
        if (msgPack != null)
        {
            try
            {
                return objectMapper.fromMsgpackAsMap(msgPack);
            }
            catch (Exception e)
            {
                final String json = getAsJsonString();

                throw new ClientException(String.format("Failed deserialize JSON '%s' to map", json), e);
            }
        }
        else
        {
            return null;
        }
    }

    public <T> T getAsType(Class<T> type)
    {
        if (msgPack != null)
        {
            try
            {
                return objectMapper.fromMsgpackAsType(msgPack, type);
            }
            catch (Exception e)
            {
                final String json = getAsJsonString();

                throw new ClientException(String.format("Failed deserialize JSON '%s' to type '%s'", json, type.getName()), e);
            }
        }
        else
        {
            return null;
        }
    }

    public void setJson(String json)
    {
        if (json != null)
        {
            msgPack = msgPackConverter.convertToMsgPack(json);
        }
        else
        {
            msgPack = null;
        }
    }

    public void setJson(InputStream stream)
    {
        if (stream != null)
        {
            msgPack = this.msgPackConverter.convertToMsgPack(stream);
        }
        else
        {
            msgPack = null;
        }
    }

    public void setAsMap(Map<String, Object> payload)
    {
        if (payload != null)
        {
            msgPack = objectMapper.toMsgpack(payload);
        }
        else
        {
            msgPack = null;
        }
    }

    public void setAsObject(Object payload)
    {
        if (payload != null)
        {
            msgPack = objectMapper.toMsgpack(payload);
        }
        else
        {
            msgPack = null;
        }
    }

}

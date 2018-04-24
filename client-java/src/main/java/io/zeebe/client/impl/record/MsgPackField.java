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

import io.zeebe.client.impl.data.MsgPackConverter;

public class MsgPackField
{
    private final MsgPackConverter msgPackConverter;

    private String json;
    private byte[] msgPack;

    public MsgPackField(MsgPackConverter msgPackConverter)
    {
        this.msgPackConverter = msgPackConverter;
    }

    public MsgPackField(MsgPackField other)
    {
        this.msgPackConverter = other.msgPackConverter;
        this.msgPack = other.msgPack;
        this.json = other.json;
    }


    public String getAsJson()
    {
        return json;
    }

    public void setJson(String json)
    {
        this.json = json;
        if (json != null)
        {
            this.msgPack = this.msgPackConverter.convertToMsgPack(json);
        }
        else
        {
            this.msgPack = null;
        }
    }

    public void setJson(InputStream stream)
    {
        if (stream != null)
        {
            setMsgPack(this.msgPackConverter.convertToMsgPack(stream));
        }
        else
        {
            setMsgPack(null);
        }
    }

    public void setMsgPack(byte[] msgPack)
    {
        this.msgPack = msgPack;
        if (msgPack != null)
        {
            this.json = this.msgPackConverter.convertToJson(msgPack);
        }
        else
        {
            this.json = null;
        }
    }

    public byte[] getMsgPack()
    {
        return msgPack;
    }

}

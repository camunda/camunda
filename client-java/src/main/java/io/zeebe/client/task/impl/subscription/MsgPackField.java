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
package io.zeebe.client.task.impl.subscription;

import io.zeebe.client.impl.data.MsgPackConverter;

public class MsgPackField
{
    protected final MsgPackConverter msgPackConverter = new MsgPackConverter();

    protected String json;
    protected byte[] msgPack;

    public String getAsJson()
    {
        return json;
    }

    public void setJson(String json)
    {
        this.json = json;
    }

    public void setMsgPack(byte[] msgPack)
    {
        this.msgPack = msgPack;
        this.json = this.msgPackConverter.convertToJson(msgPack);
    }

    public byte[] getMsgPack()
    {
        return msgPack;
    }

}

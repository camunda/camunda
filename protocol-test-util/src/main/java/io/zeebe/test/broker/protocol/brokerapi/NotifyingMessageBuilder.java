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
package io.zeebe.test.broker.protocol.brokerapi;

import org.agrona.MutableDirectBuffer;

public class NotifyingMessageBuilder<R> implements MessageBuilder<R>
{
    protected MessageBuilder<R> writer;
    protected Runnable beforeWrite;

    public NotifyingMessageBuilder(MessageBuilder<R> writer, Runnable beforeWrite)
    {
        this.writer = writer;
        this.beforeWrite = beforeWrite;
    }

    @Override
    public int getLength()
    {
        return writer.getLength();
    }

    @Override
    public void write(MutableDirectBuffer buffer, int offset)
    {
        if (beforeWrite != null)
        {
            beforeWrite.run();
        }
        writer.write(buffer, offset);
    }

    @Override
    public void initializeFrom(R context)
    {
        writer.initializeFrom(context);
    }


}

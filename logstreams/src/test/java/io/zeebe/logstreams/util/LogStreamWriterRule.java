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
package io.zeebe.logstreams.util;

import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LogStreamWriter;
import io.zeebe.logstreams.log.LogStreamWriterImpl;
import io.zeebe.test.util.TestUtil;
import org.agrona.DirectBuffer;
import org.junit.rules.ExternalResource;

public class LogStreamWriterRule extends ExternalResource
{

    private final LogStreamRule logStreamRule;

    private LogStream logStream;
    private LogStreamWriter logStreamWriter;

    public LogStreamWriterRule(final LogStreamRule logStreamRule)
    {
        this.logStreamRule = logStreamRule;
    }

    @Override
    protected void before()
    {
        logStream = logStreamRule.getLogStream();
        logStreamWriter = new LogStreamWriterImpl(logStream);
    }

    @Override
    protected void after()
    {
        logStreamWriter = null;
        logStream = null;
    }

    public long writeEvents(final int count, final DirectBuffer event)
    {
        return writeEvents(count, event, false);
    }

    public long writeEvents(final int count, final DirectBuffer event, final boolean commit)
    {
        long lastPosition = -1;
        for (int i = 1; i <= count; i++)
        {
            lastPosition = writeEventInternal(i, event);
        }

        waitForPositionToBeAppended(lastPosition);

        if (commit)
        {
            logStream.setCommitPosition(lastPosition);
        }

        return lastPosition;

    }

    public long writeEvent(final DirectBuffer event)
    {
        final long position = writeEventInternal(event);
        waitForPositionToBeAppended(position);
        return position;
    }

    private long writeEventInternal(final DirectBuffer event)
    {
        long position;
        do
        {
            position = tryWrite(event);
        }
        while (position == -1);

        return position;
    }

    private long writeEventInternal(final long key, final DirectBuffer event)
    {
        long position;
        do
        {
            position = tryWrite(key, event);
        }
        while (position == -1);

        return position;
    }

    public long tryWrite(final DirectBuffer value)
    {
        return logStreamWriter.positionAsKey().value(value).tryWrite();
    }

    public long tryWrite(final long key, final DirectBuffer value)
    {
        return logStreamWriter.key(key).value(value).tryWrite();
    }

    public void waitForPositionToBeAppended(final long position)
    {
        TestUtil.waitUntil(() -> logStream.getCurrentAppenderPosition() > position,
            "Failed to wait for position {} to be appended", position);
    }

}

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

import io.zeebe.logstreams.LogStreams;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.util.actor.ActorScheduler;
import io.zeebe.util.actor.ActorSchedulerBuilder;
import io.zeebe.util.buffer.BufferUtil;
import org.junit.rules.ExternalResource;
import org.junit.rules.TemporaryFolder;

public class LogStreamRule extends ExternalResource
{

    public static final String DEFAULT_NAME = "test-logstream";

    private final String name;
    private final TemporaryFolder temporaryFolder;

    private ActorScheduler actorScheduler;
    private LogStream logStream;

    public LogStreamRule(final TemporaryFolder temporaryFolder)
    {
       this(DEFAULT_NAME, temporaryFolder);
    }

    public LogStreamRule(final String name, final TemporaryFolder temporaryFolder)
    {
        this.name = name;
        this.temporaryFolder = temporaryFolder;
    }

    @Override
    protected void before()
    {
        actorScheduler = ActorSchedulerBuilder.createDefaultScheduler(name + "-scheduler");

        logStream = LogStreams.createFsLogStream(BufferUtil.wrapString(name), 0)
                              .logDirectory(temporaryFolder.getRoot().getAbsolutePath())
                              .actorScheduler(actorScheduler)
                              .deleteOnClose(true)
                              .build();

        logStream.open();
    }

    @Override
    protected void after()
    {
        logStream.close();
        actorScheduler.close();
    }

    public LogStream getLogStream()
    {
        return logStream;
    }

    public void setCommitPosition(final long position)
    {
        logStream.setCommitPosition(position);
    }

    public long getCommitPosition()
    {
        return logStream.getCommitPosition();
    }
}

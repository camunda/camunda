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
package io.zeebe.logstreams.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static io.zeebe.util.buffer.BufferUtil.wrapString;

import java.io.File;
import java.util.concurrent.ExecutionException;

import org.agrona.DirectBuffer;
import io.zeebe.logstreams.LogStreams;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.util.actor.ActorScheduler;
import io.zeebe.util.actor.ActorSchedulerBuilder;
import org.junit.*;
import org.junit.rules.TemporaryFolder;


public class DeleteOnCloseTest
{
    private static final DirectBuffer TOPIC_NAME = wrapString("test-topic");

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private ActorScheduler actorScheduler;

    @Before
    public void setup()
    {
        actorScheduler = ActorSchedulerBuilder.createDefaultScheduler("test");
    }

    @After
    public void destroy() throws Exception
    {
        actorScheduler.close();
    }

    @Test
    public void shouldNotDeleteOnCloseByDefault() throws InterruptedException, ExecutionException
    {
        final File logFolder = tempFolder.getRoot();

        final LogStream log = LogStreams.createFsLogStream(TOPIC_NAME, 0)
            .logRootPath(logFolder.getAbsolutePath())
            .actorScheduler(actorScheduler)
            .build();

        log.open();

        // if
        log.close();

        // then
        final File[] files = logFolder.listFiles();
        assertThat(files).isNotNull();
        assertThat(files.length).isGreaterThan(0);
    }

    @Test
    public void shouldDeleteOnCloseIfSet()
    {
        final File logFolder = tempFolder.getRoot();

        final LogStream log = LogStreams.createFsLogStream(TOPIC_NAME, 0)
            .logRootPath(logFolder.getAbsolutePath())
            .deleteOnClose(true)
            .actorScheduler(actorScheduler)
            .build();

        log.open();

        // if
        log.close();

        // then
        final File[] files = logFolder.listFiles();
        assertThat(files).isNotNull();
        assertThat(files.length).isEqualTo(0);
    }
}

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
package io.zeebe.logstreams.log;

import static io.zeebe.test.util.TestUtil.waitUntil;
import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import io.zeebe.dispatcher.Subscription;
import io.zeebe.logstreams.impl.LogStorageAppender;
import io.zeebe.logstreams.spi.LogStorage;
import io.zeebe.logstreams.util.LogStreamReaderRule;
import io.zeebe.logstreams.util.LogStreamRule;
import io.zeebe.logstreams.util.LogStreamWriterRule;
import java.util.concurrent.atomic.AtomicBoolean;
import org.agrona.DirectBuffer;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;

public class LogStorageAppenderTest {
  private static final DirectBuffer EVENT = wrapString("FOO");

  private final TemporaryFolder temporaryFolder = new TemporaryFolder();

  private final LogStreamRule logStreamRule =
      new LogStreamRule(
          temporaryFolder,
          b -> {
            b.logStorageStubber(logStorage -> spy(logStorage));
          });

  private final LogStreamWriterRule writer = new LogStreamWriterRule(logStreamRule);
  private final LogStreamReaderRule reader = new LogStreamReaderRule(logStreamRule);

  @Rule
  public RuleChain ruleChain =
      RuleChain.outerRule(temporaryFolder).around(logStreamRule).around(reader).around(writer);

  private LogStream logStream;
  private LogStorage logStorageSpy;

  @Before
  public void setup() {
    logStream = logStreamRule.getLogStream();
    logStorageSpy = logStream.getLogStorage();
  }

  @Test
  public void shouldAppendEvents() {
    writer.writeEvents(10, EVENT);

    reader.assertEvents(10, EVENT);
  }

  @Test
  public void shouldUpdateAppenderPosition() {
    final LogStorageAppender storageAppender = logStream.getLogStorageAppender();
    final long positionBefore = storageAppender.getCurrentAppenderPosition();

    writer.writeEvent(EVENT);

    waitUntil(() -> storageAppender.getCurrentAppenderPosition() > positionBefore);
  }

  @Test
  @Ignore // TODO: handle failures in append
  public void shouldDiscardEventsIfFailToAppend() {
    final Subscription subscription = logStream.getWriteBuffer().openSubscription("test");

    final LogStorageAppender logStorageAppender = logStream.getLogStorageAppender();
    final long positionBefore = logStorageAppender.getCurrentAppenderPosition();

    doReturn(-1L).when(logStorageSpy).append(any());

    // when log storage append fails
    writer.writeEvent(EVENT);

    // then
    waitUntil(() -> logStorageAppender.getCurrentAppenderPosition() > positionBefore);

    assertThat(logStorageAppender.isFailed()).isTrue();

    // verify that the segment is marked as failed
    final AtomicBoolean fragmentIsFailed = new AtomicBoolean();
    subscription.poll(
        (buffer, offset, length, streamId, isMarkedFailed) -> {
          fragmentIsFailed.set(isMarkedFailed);
          return 0;
        },
        1);

    assertThat(fragmentIsFailed).isTrue();
  }
}

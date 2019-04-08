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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.logstreams.impl.LogStreamBuilder;
import io.zeebe.logstreams.state.StateStorage;
import io.zeebe.servicecontainer.testing.ServiceContainerRule;
import io.zeebe.test.util.AutoCloseableRule;
import io.zeebe.util.sched.testing.ActorSchedulerRule;
import java.time.Duration;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.agrona.DirectBuffer;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;

public class LogStreamTest {
  public static final int PARTITION_ID = 0;

  @Rule public ExpectedException thrown = ExpectedException.none();

  public TemporaryFolder tempFolder = new TemporaryFolder();
  public TemporaryFolder snapshotFolder = new TemporaryFolder();
  public TemporaryFolder indexFolder = new TemporaryFolder();

  public AutoCloseableRule closeables = new AutoCloseableRule();
  public ActorSchedulerRule actorScheduler = new ActorSchedulerRule();
  public ServiceContainerRule serviceContainer = new ServiceContainerRule(actorScheduler);

  @Rule
  public RuleChain chain =
      RuleChain.outerRule(tempFolder)
          .around(snapshotFolder)
          .around(indexFolder)
          .around(actorScheduler)
          .around(serviceContainer)
          .around(closeables);

  protected LogStream buildLogStream(final Consumer<LogStreamBuilder> streamConfig) {
    final StateStorage stateStorage =
        new StateStorage(indexFolder.getRoot(), snapshotFolder.getRoot());

    final LogStreamBuilder builder = new LogStreamBuilder(PARTITION_ID);
    builder
        .logName("test-log-name")
        .serviceContainer(serviceContainer.get())
        .logRootPath(tempFolder.getRoot().getAbsolutePath())
        .snapshotPeriod(Duration.ofMinutes(5))
        .indexStateStorage(stateStorage);

    streamConfig.accept(builder);

    return builder.build().join();
  }

  protected LogStream buildLogStream() {
    return buildLogStream(c -> {});
  }

  @Test
  public void shouldBuildLogStream() {
    // given
    final LogStream logStream = buildLogStream();

    // when
    closeables.manage(logStream);

    // then
    assertThat(logStream.getPartitionId()).isEqualTo(PARTITION_ID);
    assertThat(logStream.getLogName()).isEqualTo("test-log-name");

    assertThat(logStream.getLogBlockIndexWriter()).isNotNull();
    assertThat(logStream.getLogBlockIndex()).isNotNull();
    assertThat(logStream.getLogStorage()).isNotNull();
    assertThat(logStream.getLogStorage().isOpen()).isTrue();

    assertThat(logStream.getCommitPosition()).isEqualTo(-1L);
    assertThat(logStream.getTerm()).isEqualTo(0);

    assertThat(logStream.getLogStorageAppender()).isNull();
    assertThat(logStream.getWriteBuffer()).isNull();
  }

  @Test
  public void shouldOpenLogStorageAppender() {
    // given
    final LogStream logStream = buildLogStream();

    // when
    logStream.openAppender().join();
    closeables.manage(logStream);

    // then
    assertThat(logStream.getLogStorageAppender()).isNotNull();
    assertThat(logStream.getWriteBuffer()).isNotNull();
  }

  @Test
  public void shouldCloseLogStorageAppender() throws Exception {
    // given
    final LogStream logStream = buildLogStream();

    logStream.openAppender().join();

    final Dispatcher writeBuffer = logStream.getWriteBuffer();

    // when
    logStream.closeAppender().join();

    // then
    assertThat(logStream.getLogStorageAppender()).isNull();
    assertThat(logStream.getWriteBuffer()).isNull();

    assertThat(writeBuffer.isClosed()).isTrue();
  }

  @Test
  public void shouldCloseLogStream() {
    // given
    final LogStream logStream = buildLogStream();

    logStream.openAppender().join();

    final Dispatcher writeBuffer = logStream.getWriteBuffer();

    // when
    logStream.close();

    // then
    assertThat(logStream.getLogStorage().isClosed()).isTrue();
    assertThat(writeBuffer.isClosed()).isTrue();
  }

  @Test
  public void shouldSetCommitPosition() throws Exception {
    // given
    final LogStream logStream = buildLogStream();

    // when
    logStream.setCommitPosition(123L);

    // then
    assertThat(logStream.getCommitPosition()).isEqualTo(123L);
  }

  @Test
  public void shouldSetTerm() throws Exception {
    // given
    final LogStream logStream = buildLogStream();

    // when
    logStream.setTerm(123);

    // then
    assertThat(logStream.getTerm()).isEqualTo(123);
  }

  @Test
  public void shouldTruncateLogStorage() {
    // given
    final LogStream logStream = buildLogStream();

    logStream.openAppender().join();
    closeables.manage(logStream);

    final long firstPosition = writeEvent(logStream);
    final long secondPosition = writeEvent(logStream);

    assertThat(events(logStream).count()).isEqualTo(2);

    // when
    logStream.truncate(secondPosition);

    // then
    assertThat(events(logStream).count()).isEqualTo(1);
    assertThat(events(logStream).findFirst().get().getPosition()).isEqualTo(firstPosition);
  }

  @Test
  public void shouldTruncateLogStorageAfterCommittedPosition() {
    // given
    final LogStream logStream = buildLogStream();

    logStream.openAppender().join();
    closeables.manage(logStream);

    final long firstPosition = writeEvent(logStream);
    final long secondPosition = writeEvent(logStream);

    logStream.setCommitPosition(firstPosition);

    // when
    logStream.truncate(secondPosition);

    // then
    assertThat(events(logStream).count()).isEqualTo(1);
    assertThat(events(logStream).findFirst().get().getPosition()).isEqualTo(firstPosition);
  }

  @Test
  public void shouldTruncateWhenPositionIsNotAnEventPosition() {
    // given
    final LogStream logStream = buildLogStream();

    logStream.openAppender().join();
    closeables.manage(logStream);

    final long firstPosition = writeEvent(logStream);
    final long secondPosition = writeEvent(logStream);

    // when
    logStream.truncate(secondPosition - 1);

    // then
    assertThat(events(logStream).count()).isEqualTo(1);
    assertThat(events(logStream).findFirst().get().getPosition()).isEqualTo(firstPosition);
  }

  @Test
  public void shouldWriteNewEventAfterTruncation() {
    // given
    final LogStream logStream = buildLogStream();

    logStream.openAppender().join();
    closeables.manage(logStream);

    final long firstPosition = writeEvent(logStream);

    logStream.truncate(firstPosition);

    // when
    final long secondPosition = writeEvent(logStream);

    // then
    assertThat(events(logStream).count()).isEqualTo(1);
    assertThat(events(logStream).findFirst().get().getPosition()).isEqualTo(secondPosition);
  }

  @Test
  public void shouldNotTruncateIfPositionIsAlreadyCommitted() {
    // given
    final LogStream logStream = buildLogStream();

    closeables.manage(logStream);

    logStream.setCommitPosition(100L);

    // when
    assertThatThrownBy(() -> logStream.truncate(100L))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Can't truncate position which is already committed");
  }

  @Test
  public void shouldNotTruncateIfPositionIsGreaterThanCurrentHead() {
    // given
    final LogStream logStream = buildLogStream();

    logStream.openAppender().join();
    closeables.manage(logStream);

    // when
    final long nonExistingPosition = Long.MAX_VALUE;

    assertThatThrownBy(() -> logStream.truncate(nonExistingPosition))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Truncation failed! Position " + nonExistingPosition + " was not found.");
  }

  private Stream<LoggedEvent> events(final LogStream stream) {
    final BufferedLogStreamReader reader = new BufferedLogStreamReader(stream, true);
    closeables.manage(reader);

    reader.seekToFirstEvent();
    final Iterable<LoggedEvent> iterable = () -> reader;
    return StreamSupport.stream(iterable.spliterator(), false);
  }

  static long writeEvent(final LogStream logStream) {
    return writeEvent(logStream, wrapString("event"));
  }

  static long writeEvent(final LogStream logStream, DirectBuffer value) {
    final LogStreamWriterImpl writer = new LogStreamWriterImpl(logStream);

    long position = -1L;

    while (position < 0) {
      position = writer.value(value).tryWrite();
    }

    final long writtenEventPosition = position;
    waitUntil(
        () ->
            logStream.getLogStorageAppender().getCurrentAppenderPosition() > writtenEventPosition);

    return position;
  }
}

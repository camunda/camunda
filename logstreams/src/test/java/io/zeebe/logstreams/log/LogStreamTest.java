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

import static io.zeebe.logstreams.impl.service.LogStreamServiceNames.distributedLogPartitionServiceName;
import static io.zeebe.test.util.TestUtil.waitUntil;
import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.distributedlog.CommitLogEvent;
import io.zeebe.distributedlog.impl.DistributedLogstreamPartition;
import io.zeebe.logstreams.impl.LogStreamBuilder;
import io.zeebe.servicecontainer.testing.ServiceContainerRule;
import io.zeebe.test.util.AutoCloseableRule;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.testing.ActorSchedulerRule;
import java.io.File;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;
import org.mockito.stubbing.Answer;

public class LogStreamTest {
  public static final int PARTITION_ID = 0;

  @Rule public ExpectedException thrown = ExpectedException.none();

  public TemporaryFolder tempFolder = new TemporaryFolder();
  public AutoCloseableRule closeables = new AutoCloseableRule();
  public ActorSchedulerRule actorScheduler = new ActorSchedulerRule();
  public ServiceContainerRule serviceContainer = new ServiceContainerRule(actorScheduler);

  @Rule
  public RuleChain chain =
      RuleChain.outerRule(tempFolder)
          .around(actorScheduler)
          .around(serviceContainer)
          .around(closeables);

  protected LogStream buildLogStream(final Consumer<LogStreamBuilder> streamConfig) {

    final DistributedLogstreamPartition mockDistLog = mock(DistributedLogstreamPartition.class);
    serviceContainer
        .get()
        .createService(distributedLogPartitionServiceName("test-log-name"), () -> mockDistLog)
        .install();

    final LogStreamBuilder builder = new LogStreamBuilder(PARTITION_ID);
    builder
        .logName("test-log-name")
        .serviceContainer(serviceContainer.get())
        .logRootPath(tempFolder.getRoot().getAbsolutePath())
        .snapshotPeriod(Duration.ofMinutes(5));

    streamConfig.accept(builder);

    final ActorFuture<LogStream> logStreamFuture = builder.build();

    doAnswer(
            (Answer<Void>)
                invocation -> {
                  final Object[] arguments = invocation.getArguments();
                  if (arguments != null
                      && arguments.length > 1
                      && arguments[0] != null
                      && arguments[1] != null) {
                    final ByteBuffer buffer = (ByteBuffer) arguments[0];
                    final long pos = (long) arguments[1];
                    final byte[] bytes = new byte[buffer.remaining()];
                    buffer.get(bytes);
                    logStreamFuture
                        .get()
                        .getLogStorageCommitter()
                        .onCommit(new CommitLogEvent(pos, bytes));
                  }
                  return null;
                })
        .when(mockDistLog)
        .append(any(ByteBuffer.class), anyLong());

    final LogStream logStream = logStreamFuture.join();
    return logStream;
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
  public void shouldDeleteOnClose() {
    final File logDir = tempFolder.getRoot();
    final LogStream logStream =
        buildLogStream(b -> b.logRootPath(logDir.getAbsolutePath()).deleteOnClose(true));

    // when
    logStream.close();

    // then
    final File[] files = logDir.listFiles();
    assertThat(files).isNotNull();
    assertThat(files.length).isEqualTo(0);
  }

  @Test
  public void shouldNotDeleteOnCloseByDefault() {
    final File logDir = tempFolder.getRoot();
    final LogStream logStream = buildLogStream(b -> b.logRootPath(logDir.getAbsolutePath()));

    // when
    logStream.close();

    // then
    final File[] files = logDir.listFiles();
    assertThat(files).isNotNull();
    assertThat(files.length).isGreaterThan(0);
  }

  @Test
  @Ignore // events are appended only after committed. So cannot truncate.
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
  @Ignore // events are appended only after committed. So cannot truncate.
  // https://github.com/zeebe-io/zeebe/issues/2058
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
  @Ignore // events are appended only after committed. So cannot truncate.
  // https://github.com/zeebe-io/zeebe/issues/2058
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
  @Ignore // events are appended only after committed. So cannot truncate.
  // https://github.com/zeebe-io/zeebe/issues/2058
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
    final BufferedLogStreamReader reader = new BufferedLogStreamReader(stream);
    closeables.manage(reader);

    reader.seekToFirstEvent();
    final Iterable<LoggedEvent> iterable = () -> reader;
    return StreamSupport.stream(iterable.spliterator(), false);
  }

  private long writeEvent(final LogStream logStream) {
    final LogStreamWriterImpl writer = new LogStreamWriterImpl(logStream);

    long position = -1L;

    while (position < 0) {
      position = writer.positionAsKey().value(wrapString("event")).tryWrite();
    }

    final long writtenEventPosition = position;
    waitUntil(() -> logStream.getCommitPosition() > writtenEventPosition);

    return position;
  }
}

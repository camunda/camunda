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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.distributedlog.DistributedLogstreamService;
import io.zeebe.distributedlog.impl.DefaultDistributedLogstreamService;
import io.zeebe.distributedlog.impl.DistributedLogstreamPartition;
import io.zeebe.distributedlog.impl.DistributedLogstreamServiceConfig;
import io.zeebe.logstreams.impl.LogStreamBuilder;
import io.zeebe.logstreams.state.StateStorage;
import io.zeebe.servicecontainer.testing.ServiceContainerRule;
import io.zeebe.test.util.AutoCloseableRule;
import io.zeebe.util.sched.testing.ActorSchedulerRule;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.agrona.DirectBuffer;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;
import org.mockito.internal.util.reflection.FieldSetter;
import org.mockito.stubbing.Answer;

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

    final LogStream logStream = builder.build().join();

    final DistributedLogstreamPartition mockDistLog = mock(DistributedLogstreamPartition.class);

    final DistributedLogstreamService distributedLogImpl =
        new DefaultDistributedLogstreamService(new DistributedLogstreamServiceConfig());

    final String nodeId = "0";
    try {
      FieldSetter.setField(
          distributedLogImpl,
          DefaultDistributedLogstreamService.class.getDeclaredField("logStream"),
          logStream);

      FieldSetter.setField(
          distributedLogImpl,
          DefaultDistributedLogstreamService.class.getDeclaredField("logStorage"),
          logStream.getLogStorage());

      FieldSetter.setField(
          distributedLogImpl,
          DefaultDistributedLogstreamService.class.getDeclaredField("currentLeader"),
          nodeId);

    } catch (NoSuchFieldException e) {
      e.printStackTrace();
    }

    doAnswer(
            (Answer<CompletableFuture<Long>>)
                invocation -> {
                  final Object[] arguments = invocation.getArguments();
                  if (arguments != null
                      && arguments.length > 1
                      && arguments[0] != null
                      && arguments[1] != null) {
                    final byte[] bytes = (byte[]) arguments[0];
                    final long pos = (long) arguments[1];
                    return CompletableFuture.completedFuture(
                        distributedLogImpl.append(nodeId, pos, bytes));
                  }
                  return null;
                })
        .when(mockDistLog)
        .asyncAppend(any(), anyLong());

    serviceContainer
        .get()
        .createService(distributedLogPartitionServiceName("test-log-name"), () -> mockDistLog)
        .install()
        .join();

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
  public void shouldCloseLogStorageAppender() {
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
    waitUntil(() -> logStream.getCommitPosition() >= writtenEventPosition);

    return position;
  }
}

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

import static io.zeebe.dispatcher.impl.log.DataFrameDescriptor.alignedLength;
import static io.zeebe.logstreams.impl.LogEntryDescriptor.HEADER_BLOCK_LENGTH;
import static io.zeebe.logstreams.impl.service.LogStreamServiceNames.distributedLogPartitionServiceName;
import static io.zeebe.logstreams.log.LogStreamTest.writeEvent;
import static io.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import io.zeebe.distributedlog.DistributedLogstreamService;
import io.zeebe.distributedlog.impl.DefaultDistributedLogstreamService;
import io.zeebe.distributedlog.impl.DistributedLogstreamPartition;
import io.zeebe.distributedlog.impl.DistributedLogstreamServiceConfig;
import io.zeebe.logstreams.impl.LogStreamBuilder;
import io.zeebe.logstreams.impl.log.fs.FsLogSegmentDescriptor;
import io.zeebe.logstreams.impl.log.index.LogBlockIndexContext;
import io.zeebe.logstreams.state.StateStorage;
import io.zeebe.servicecontainer.testing.ServiceContainerRule;
import io.zeebe.test.util.AutoCloseableRule;
import io.zeebe.util.buffer.BufferUtil;
import io.zeebe.util.sched.testing.ActorSchedulerRule;
import java.io.File;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;
import org.mockito.internal.util.reflection.FieldSetter;
import org.mockito.stubbing.Answer;

public class LogStreamDeleteTest {
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

  private long firstPosition;
  private long secondPosition;
  private long thirdPosition;
  private long fourthPosition;
  private LogBlockIndexContext indexContext;

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
  public void shouldDeleteFromLogStream() {
    // given
    final LogStream logStream = prepareLogstream();
    logStream.setExporterPositionSupplier(() -> Long.MAX_VALUE);

    // when
    logStream.delete(fourthPosition);

    // then
    assertThat(events(logStream).count()).isEqualTo(2);

    assertThat(events(logStream).anyMatch(e -> e.getPosition() == firstPosition)).isFalse();
    assertThat(events(logStream).anyMatch(e -> e.getPosition() == secondPosition)).isFalse();

    assertThat(events(logStream).findFirst().get().getPosition()).isEqualTo(thirdPosition);
    assertThat(events(logStream).filter(e -> e.getPosition() == fourthPosition).findAny())
        .isNotEmpty();
  }

  @Test
  public void shouldDeleteUntilLastBlockIndexAddress() {
    // given
    final LogStream logStream = prepareLogstream();
    logStream.setExporterPositionSupplier(() -> Long.MAX_VALUE);

    // when
    logStream.delete(Long.MAX_VALUE);

    // then - segment 0 and 1 are removed
    assertThat(events(logStream).count()).isEqualTo(2);

    assertThat(events(logStream).filter(e -> e.getPosition() == firstPosition).findAny()).isEmpty();
    assertThat(events(logStream).filter(e -> e.getPosition() == secondPosition).findAny())
        .isEmpty();

    assertThat(events(logStream).findFirst().get().getPosition()).isEqualTo(thirdPosition);
    assertThat(events(logStream).filter(e -> e.getPosition() == fourthPosition).findAny())
        .isNotEmpty();
  }

  @Test
  public void shouldNotDeleteOnNegativePosition() {
    // given
    final LogStream logStream = prepareLogstream();

    // when
    logStream.delete(-1);

    // then - segment 0 and 1 are removed
    assertThat(events(logStream).count()).isEqualTo(4);

    assertThat(events(logStream).filter(e -> e.getPosition() == firstPosition).findAny())
        .isNotEmpty();
    assertThat(events(logStream).filter(e -> e.getPosition() == secondPosition).findAny())
        .isNotEmpty();
    assertThat(events(logStream).filter(e -> e.getPosition() == thirdPosition).findAny())
        .isNotEmpty();
    assertThat(events(logStream).filter(e -> e.getPosition() == fourthPosition).findAny())
        .isNotEmpty();
  }

  @Test
  public void shouldDeleteMinExportedPosition() {
    // given
    final LogStream logStream = prepareLogstream();

    // when
    logStream.setExporterPositionSupplier(() -> thirdPosition);
    logStream.delete(fourthPosition);

    // then
    assertThat(events(logStream).count()).isEqualTo(3);

    assertThat(events(logStream).filter(e -> e.getPosition() == firstPosition).findAny()).isEmpty();
    assertThat(events(logStream).filter(e -> e.getPosition() == secondPosition).findAny())
        .isNotEmpty();
    assertThat(events(logStream).filter(e -> e.getPosition() == thirdPosition).findAny())
        .isNotEmpty();
    assertThat(events(logStream).filter(e -> e.getPosition() == fourthPosition).findAny())
        .isNotEmpty();
  }

  @Test
  public void shouldNotDeleteWithoutSupplierConfigured() {
    // given
    final LogStream logStream = prepareLogstream();

    // when
    logStream.delete(Long.MAX_VALUE);

    // then
    assertThat(events(logStream).count()).isEqualTo(4);
    assertThat(
        events(logStream)
            .allMatch(
                e ->
                    e.getPosition() == firstPosition
                        || e.getPosition() == secondPosition
                        || e.getPosition() == thirdPosition
                        || e.getPosition() == fourthPosition));
  }

  private LogStream prepareLogstream() {
    final int segmentSize = 1024 * 8;
    final int remainingCapacity =
        (segmentSize
            - FsLogSegmentDescriptor.METADATA_LENGTH
            - alignedLength(HEADER_BLOCK_LENGTH + 2 + 8)
            - 1);
    final int idxSize = (segmentSize - FsLogSegmentDescriptor.METADATA_LENGTH);
    final LogStream logStream =
        buildLogStream(
            c -> c.logSegmentSize(segmentSize).readBlockSize(idxSize).indexBlockSize(idxSize));
    logStream.openAppender().join();
    closeables.manage(logStream);
    final byte[] largeEvent = new byte[remainingCapacity];

    // log storage always returns on append as address (segment id, segment OFFSET)
    // where offset should be the start of the event to be written
    // this is in most cases true, besides the case where the end of an segment is reached
    // If the segment is full on append - a new segment is created, but as offset
    // the old position is used (which is the end of the segment) and the old segment id
    // that is the reason why the tests will only delete 2 segments instead of expected three

    // written from segment 0 4096 -> 8192, idx block address 4096
    firstPosition = writeEvent(logStream, BufferUtil.wrapArray(largeEvent));
    // written from segment 1 4096 -> 8192, but idx block address segment 0 - 8192
    secondPosition = writeEvent(logStream, BufferUtil.wrapArray(largeEvent));
    // written from segment 2 4096 -> 8192, but idx block address segment 1 - 8192
    thirdPosition = writeEvent(logStream, BufferUtil.wrapArray(largeEvent));
    // written from segment 3 4096 -> 8192, but idx block address segment 2 - 8192
    fourthPosition = writeEvent(logStream, BufferUtil.wrapArray(largeEvent));

    logStream.setCommitPosition(fourthPosition);
    waitUntil(() -> logStream.getLogBlockIndex().getLastPosition() >= fourthPosition);

    return logStream;
  }

  private Stream<LoggedEvent> events(final LogStream stream) {
    final BufferedLogStreamReader reader = new BufferedLogStreamReader(stream);
    closeables.manage(reader);

    reader.seekToFirstEvent();
    final Iterable<LoggedEvent> iterable = () -> reader;
    return StreamSupport.stream(iterable.spliterator(), false);
  }
}

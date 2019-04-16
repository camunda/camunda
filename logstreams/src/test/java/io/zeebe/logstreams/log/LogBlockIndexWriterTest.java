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
import static org.assertj.core.api.Java6Assertions.assertThat;

import io.zeebe.dispatcher.impl.log.DataFrameDescriptor;
import io.zeebe.logstreams.impl.LogBlockIndexWriter;
import io.zeebe.logstreams.impl.LogEntryDescriptor;
import io.zeebe.logstreams.impl.log.index.LogBlockIndex;
import io.zeebe.logstreams.impl.log.index.LogBlockIndexContext;
import io.zeebe.logstreams.spi.LogStorage;
import io.zeebe.logstreams.state.StateStorage;
import io.zeebe.logstreams.util.LogStreamRule;
import io.zeebe.logstreams.util.LogStreamWriterRule;
import io.zeebe.util.metrics.Metric;
import java.nio.ByteBuffer;
import java.time.Duration;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;

public class LogBlockIndexWriterTest {
  private static final DirectBuffer EVENT_1 = wrapString("FOO");
  private static final DirectBuffer EVENT_2 = wrapString("BAR");

  private static final int EVENT_SIZE = EVENT_1.capacity();
  private static final int FRAGMENT_SIZE =
      DataFrameDescriptor.alignedFramedLength(LogEntryDescriptor.HEADER_BLOCK_LENGTH + EVENT_SIZE);
  private static final int INDEX_BLOCK_SIZE = 2 * FRAGMENT_SIZE;

  private static final Duration SNAPSHOT_INTERVAL = Duration.ofSeconds(1);

  private final TemporaryFolder temporaryFolder = new TemporaryFolder();

  private final LogStreamRule logStreamRule =
      new LogStreamRule(
          temporaryFolder,
          b ->
              b.indexBlockSize(INDEX_BLOCK_SIZE)
                  .readBlockSize(FRAGMENT_SIZE)
                  .snapshotPeriod(SNAPSHOT_INTERVAL));

  private final LogStreamWriterRule writer = new LogStreamWriterRule(logStreamRule);

  @Rule
  public RuleChain ruleChain =
      RuleChain.outerRule(temporaryFolder).around(logStreamRule).around(writer);

  private LogBlockIndex blockIndex;
  private LogStorage logStorage;
  private StateStorage stateStorage;
  private LogBlockIndexContext indexContext;

  @Before
  public void setup() {
    final LogStream logStream = logStreamRule.getLogStream();
    blockIndex = logStream.getLogBlockIndex();
    indexContext = blockIndex.createLogBlockIndexContext();
    logStorage = logStream.getLogStorage();
    stateStorage = logStreamRule.getStateStorage();
  }

  @Test
  public void shouldAppendBlockWhenIndexBlockSizeIsReached() {
    // given
    final long firstEventPosition = writer.writeEvent(EVENT_1);
    writer.writeEvent(EVENT_2);

    // when
    waitUntil(() -> !blockIndex.isEmpty(indexContext));

    // then
    assertThat(blockIndex.getLastPosition()).isEqualTo(firstEventPosition);
  }

  @Test
  public void shouldAppendBlockWithPositionAndAddressOfFirstEventInTheBlock() {
    // given
    final long firstEventPosition = writer.writeEvent(EVENT_1);
    writer.writeEvent(EVENT_2);

    // when
    waitUntil(() -> !blockIndex.isEmpty(indexContext));

    // then
    final long indexPosition = blockIndex.getLastPosition();
    final long indexAddress = blockIndex.lookupBlockAddress(indexContext, indexPosition);

    assertThat(indexPosition).isEqualTo(firstEventPosition);
    assertThat(readEventAtAddress(indexAddress)).isEqualTo(EVENT_1);
  }

  @Test
  public void shouldAppendMultipleBlocks() {
    // given
    writer.writeEvents(8, EVENT_1);

    final long lastBlockPosition = writer.writeEvent(EVENT_2);
    final long lastEventPosition = writer.writeEvent(EVENT_2);

    // when
    waitUntil(() -> blockIndex.getLastPosition() == lastBlockPosition);

    // then
    assertCorrectBlockAndEvent(blockIndex, lastBlockPosition, lastEventPosition, EVENT_2);
  }

  @Test
  public void shouldWriteSnapshot() {
    // given
    writer.writeEvents(2, EVENT_1);
    final long eventPositionOfLastBlock = writer.writeEvent(EVENT_1);
    writer.writeEvent(EVENT_2);

    waitUntil(() -> blockIndex.getLastPosition() == eventPositionOfLastBlock);
    assertThat(stateStorage.list()).isEmpty();

    // when
    logStreamRule.getClock().addTime(SNAPSHOT_INTERVAL);
    waitUntil(() -> getSnapshotCount() > 0);

    // then
    assertThat(stateStorage.list()).hasSize(1);
  }

  @Test
  public void shouldWriteSnapshotWithPositionOfLastBlock() {
    // given
    final long eventPositionOfLastBlock = writer.writeEvent(EVENT_1);
    writer.writeEvent(EVENT_1);
    writer.writeEvent(EVENT_2);

    // when
    waitUntil(() -> !blockIndex.isEmpty(indexContext));
    assertThat(stateStorage.list()).isEmpty();

    logStreamRule.getClock().addTime(SNAPSHOT_INTERVAL);
    waitUntil(() -> getSnapshotCount() > 0);

    // then
    assertThat(stateStorage.list()).hasSize(1);

    final long snapshotWrittenPosition = Long.parseLong(stateStorage.list().get(0).getName());
    assertThat(snapshotWrittenPosition).isEqualTo(eventPositionOfLastBlock);
  }

  @Test
  public void shouldNotWriteEmptySnapshot() throws InterruptedException {
    // given
    logStreamRule.getClock().addTime(SNAPSHOT_INTERVAL);

    // when
    Thread.sleep(100);

    // then
    assertThat(stateStorage.list()).isEmpty();
  }

  @Test
  public void shouldRecoverBlockIndex() {
    // given
    logStreamRule.getClock().pinCurrentTime();
    writer.writeEvents(2, EVENT_1);
    final long lastBlockPosition = writer.writeEvents(1, EVENT_2);
    final long lastEventPosition = writer.writeEvents(1, EVENT_2);

    logStreamRule.closeLogStream();
    assertThat(stateStorage.list()).isEmpty();

    // when
    logStreamRule.openLogStream();
    logStreamRule.getLogStream().setCommitPosition(lastEventPosition);
    logStorage = logStreamRule.getLogStream().getLogStorage();
    final LogBlockIndex newIndex = logStreamRule.getLogStream().getLogBlockIndex();
    indexContext = newIndex.createLogBlockIndexContext();

    // then
    waitUntil(() -> newIndex.getLastPosition() == lastBlockPosition);
    assertCorrectBlockAndEvent(newIndex, lastBlockPosition, lastEventPosition, EVENT_2);
  }

  @Test
  public void shouldAppendBlockAfterRecover() {
    // given
    logStreamRule.getClock().pinCurrentTime();
    writer.writeEvent(EVENT_1);
    final long lastPos = writer.writeEvent(EVENT_1);

    logStreamRule.closeLogStream();
    assertThat(stateStorage.list()).isEmpty();

    // when
    logStreamRule.openLogStream();
    logStreamRule.getLogStream().setCommitPosition(lastPos);
    logStorage = logStreamRule.getLogStream().getLogStorage();
    final LogBlockIndex newIndex = logStreamRule.getLogStream().getLogBlockIndex();
    indexContext = newIndex.createLogBlockIndexContext();

    waitUntil(() -> !newIndex.isEmpty(indexContext));

    writer.wrap(logStreamRule);
    final long lastBlockPosition = writer.writeEvents(1, EVENT_2);
    final long lastEventPosition = writer.writeEvents(1, EVENT_2);

    // then
    waitUntil(() -> newIndex.getLastPosition() == lastBlockPosition);
    assertCorrectBlockAndEvent(newIndex, lastBlockPosition, lastEventPosition, EVENT_2);
  }

  @Test
  public void shouldRecoverBlockIndexFromSnapshot() {
    // given
    logStreamRule.getClock().pinCurrentTime();
    writer.writeEvents(2, EVENT_1);
    final long lastBlockPosition = writer.writeEvents(1, EVENT_2);
    final long lastEventPosition = writer.writeEvents(1, EVENT_2);

    waitUntil(() -> blockIndex.getLastPosition() == lastBlockPosition);
    logStreamRule.getClock().addTime(SNAPSHOT_INTERVAL);

    waitUntil(() -> getSnapshotCount() > 0);
    logStreamRule.closeLogStream();

    // when
    logStreamRule.openLogStream();
    logStorage = logStreamRule.getLogStream().getLogStorage();
    final LogBlockIndex newIndex = logStreamRule.getLogStream().getLogBlockIndex();
    indexContext = newIndex.createLogBlockIndexContext();

    // then
    waitUntil(() -> newIndex.getLastPosition() == lastBlockPosition);
    assertCorrectBlockAndEvent(newIndex, lastBlockPosition, lastEventPosition, EVENT_2);
  }

  @Test
  public void shouldAppendBlockAfterRecoverFromSnapshot() {
    // given
    logStreamRule.getClock().pinCurrentTime();
    writer.writeEvents(2, EVENT_1);

    waitUntil(() -> !blockIndex.isEmpty(indexContext));

    logStreamRule.getClock().addTime(SNAPSHOT_INTERVAL);
    waitUntil(() -> getSnapshotCount() > 0);

    final long commitPosition = logStreamRule.getCommitPosition();
    logStreamRule.closeLogStream();

    // when
    logStreamRule.openLogStream();
    logStreamRule.setCommitPosition(commitPosition);
    logStorage = logStreamRule.getLogStream().getLogStorage();
    final LogBlockIndex newIndex = logStreamRule.getLogStream().getLogBlockIndex();
    indexContext = newIndex.createLogBlockIndexContext();

    writer.wrap(logStreamRule);
    final long lastBlockPosition = writer.writeEvents(1, EVENT_2);
    final long lastEventPosition = writer.writeEvents(1, EVENT_2);

    // then
    waitUntil(() -> newIndex.getLastPosition() == lastBlockPosition);
    assertCorrectBlockAndEvent(newIndex, lastBlockPosition, lastEventPosition, EVENT_2);
  }

  private void assertCorrectBlockAndEvent(
      LogBlockIndex newIndex,
      long expectedBlockPosition,
      long lastEventPosition,
      DirectBuffer event) {
    assertThat(newIndex.lookupBlockPosition(indexContext, lastEventPosition))
        .isEqualTo(expectedBlockPosition);
    assertThat(readEventAtAddress(newIndex.lookupBlockAddress(indexContext, lastEventPosition)))
        .isEqualTo(event);
  }

  private UnsafeBuffer readEventAtAddress(final long indexAddress) {
    final ByteBuffer buffer = ByteBuffer.allocate(FRAGMENT_SIZE);
    logStorage.read(buffer, indexAddress);

    final int headerLength =
        DataFrameDescriptor.HEADER_LENGTH + LogEntryDescriptor.HEADER_BLOCK_LENGTH;
    return new UnsafeBuffer(buffer, headerLength, EVENT_SIZE);
  }

  private long getSnapshotCount() {
    final LogBlockIndexWriter indexWriter = logStreamRule.getLogStream().getLogBlockIndexWriter();
    final Metric snapshotsCreated = indexWriter.getSnapshotsCreated();
    return snapshotsCreated.get();
  }
}

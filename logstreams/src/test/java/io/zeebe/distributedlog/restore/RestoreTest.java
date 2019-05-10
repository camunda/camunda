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
package io.zeebe.distributedlog.restore;

import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Java6Assertions.assertThat;

import io.zeebe.db.impl.DefaultColumnFamily;
import io.zeebe.db.impl.rocksdb.ZeebeRocksDbFactory;
import io.zeebe.distributedlog.impl.DefaultDistributedLogstreamService;
import io.zeebe.distributedlog.impl.LogstreamConfig;
import io.zeebe.distributedlog.restore.RestoreInfoResponse.ReplicationTarget;
import io.zeebe.distributedlog.restore.impl.ControllableSnapshotRestoreContext;
import io.zeebe.distributedlog.restore.impl.DefaultRestoreInfoResponse;
import io.zeebe.distributedlog.restore.impl.ReplicatingRestoreClient;
import io.zeebe.distributedlog.restore.impl.ReplicatingRestoreClientProvider;
import io.zeebe.logstreams.state.SnapshotChunk;
import io.zeebe.logstreams.state.SnapshotReplication;
import io.zeebe.logstreams.state.StateSnapshotController;
import io.zeebe.logstreams.state.StateStorage;
import io.zeebe.logstreams.util.LogStreamReaderRule;
import io.zeebe.logstreams.util.LogStreamRule;
import io.zeebe.logstreams.util.LogStreamWriterRule;
import io.zeebe.logstreams.util.RocksDBWrapper;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import org.agrona.DirectBuffer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;

public class RestoreTest {

  private static final int VALUE = 0xCAFE;
  private static final String KEY = "test";
  private static final DirectBuffer EVENT = wrapString("FOO");

  public TemporaryFolder temporaryFolderClient = new TemporaryFolder();
  public LogStreamRule logStreamRuleClient = new LogStreamRule(temporaryFolderClient);
  public LogStreamWriterRule writerClient = new LogStreamWriterRule(logStreamRuleClient);
  public LogStreamReaderRule readerClient = new LogStreamReaderRule(logStreamRuleClient);

  public TemporaryFolder temporaryFolderServer = new TemporaryFolder();
  public LogStreamRule logStreamRuleServer = new LogStreamRule(temporaryFolderServer);
  public LogStreamWriterRule writerServer = new LogStreamWriterRule(logStreamRuleServer);
  public LogStreamReaderRule readerServer = new LogStreamReaderRule(logStreamRuleServer);

  @Rule
  public RuleChain ruleChain =
      RuleChain.outerRule(temporaryFolderClient)
          .around(logStreamRuleClient)
          .around(readerClient)
          .around(writerClient)
          .around(temporaryFolderServer)
          .around(logStreamRuleServer)
          .around(readerServer)
          .around(writerServer);

  private ControllableSnapshotRestoreContext snapshotRestoreContext;

  private StateSnapshotController replicatorSnapshotController;
  private StateStorage receiverStorage;
  private ReplicatingRestoreClient restoreClient;
  private String clientNodeId = "0";
  private DefaultDistributedLogstreamService distributedLog;

  @Before
  public void setUp() throws IOException {
    final File runtimeDirectory = temporaryFolderServer.newFolder("runtime");
    final File snapshotsDirectory = temporaryFolderServer.newFolder("snapshots");
    final StateStorage storage = new StateStorage(runtimeDirectory, snapshotsDirectory);

    final File receiverRuntimeDirectory = temporaryFolderClient.newFolder("runtime-receiver");
    final File receiverSnapshotsDirectory = temporaryFolderClient.newFolder("snapshots-receiver");
    final Replicator processorReplicator = new Replicator();
    receiverStorage = new StateStorage(receiverRuntimeDirectory, receiverSnapshotsDirectory);
    replicatorSnapshotController =
        new StateSnapshotController(
            ZeebeRocksDbFactory.newFactory(DefaultColumnFamily.class),
            storage,
            processorReplicator,
            1);
    final RocksDBWrapper wrapper = new RocksDBWrapper();
    wrapper.wrap(replicatorSnapshotController.openDb());
    wrapper.putInt(KEY, VALUE);

    snapshotRestoreContext = new ControllableSnapshotRestoreContext();
    snapshotRestoreContext.setProcessorSnapshotReplicationConsumer(processorReplicator);
    snapshotRestoreContext.setProcessorStateStorage(receiverStorage);

    restoreClient =
        new ReplicatingRestoreClient(
            replicatorSnapshotController, logStreamRuleServer.getLogStream());

    final ReplicatingRestoreClientProvider provider =
        new ReplicatingRestoreClientProvider(restoreClient, snapshotRestoreContext);
    LogstreamConfig.putRestoreFactory(clientNodeId, provider);

    distributedLog =
        new DefaultDistributedLogstreamService(logStreamRuleClient.getLogStream(), clientNodeId);
  }

  @Test
  public void shouldRestoreFromLogEvents() {
    // given
    final int numEventsInBackup = 10;
    final int numExtraEvents = 5;

    final long backupPosition = writerServer.writeEvents(numEventsInBackup, EVENT);
    writerServer.writeEvents(numExtraEvents, EVENT);

    final DefaultRestoreInfoResponse defaultRestoreInfoResponse = new DefaultRestoreInfoResponse();
    defaultRestoreInfoResponse.setReplicationTarget(ReplicationTarget.EVENTS);
    restoreClient.setRestoreInfoResponse(defaultRestoreInfoResponse);

    // when
    distributedLog.restore(backupPosition);

    // then
    readerClient.assertEvents(numEventsInBackup, EVENT);
  }

  @Test
  public void shouldRestoreFromSnapshotAtBackUpPosition() {
    final int numEventsInSnapshot = 10;

    final long snapshotPosition = writerServer.writeEvents(numEventsInSnapshot, EVENT);
    replicatorSnapshotController.takeSnapshot(snapshotPosition);

    final DefaultRestoreInfoResponse defaultRestoreInfoResponse = new DefaultRestoreInfoResponse();
    defaultRestoreInfoResponse.setReplicationTarget(ReplicationTarget.SNAPSHOT);
    restoreClient.setRestoreInfoResponse(defaultRestoreInfoResponse);

    snapshotRestoreContext.setProcessorPositionSupplier(() -> snapshotPosition);
    snapshotRestoreContext.setExporterPositionSupplier(() -> snapshotPosition);

    distributedLog.restore(snapshotPosition);

    assertThat(readerClient.readEvents().size()).isEqualTo(1);
    assertThat(
            Arrays.stream(receiverStorage.getSnapshotsDirectory().listFiles())
                .anyMatch(f -> f.getName().equals(String.valueOf(snapshotPosition))))
        .isTrue();
  }

  @Test
  public void shouldRestoreFromSnapshotLessThanBackUpPosition() {

    // given
    final int numEventsInSnapshot = 5;
    final int numEventsAfterSnapshotInBackup = 10;

    final long snapshotPosition = writerServer.writeEvents(numEventsInSnapshot, EVENT);
    replicatorSnapshotController.takeSnapshot(snapshotPosition);
    final long backupPosition = writerServer.writeEvents(numEventsAfterSnapshotInBackup, EVENT);

    final DefaultRestoreInfoResponse defaultRestoreInfoResponse = new DefaultRestoreInfoResponse();
    defaultRestoreInfoResponse.setReplicationTarget(ReplicationTarget.SNAPSHOT);
    restoreClient.setRestoreInfoResponse(defaultRestoreInfoResponse);

    snapshotRestoreContext.setProcessorPositionSupplier(() -> snapshotPosition);
    snapshotRestoreContext.setExporterPositionSupplier(() -> snapshotPosition);

    // when
    distributedLog.restore(backupPosition);

    // then
    assertThat(readerClient.readEvents().size()).isEqualTo(numEventsAfterSnapshotInBackup + 1);
    assertThat(
            Arrays.stream(receiverStorage.getSnapshotsDirectory().listFiles())
                .anyMatch(f -> f.getName().equals(String.valueOf(snapshotPosition))))
        .isTrue();
  }

  @Test
  public void shouldRestoreFromSnapshotGreaterThanBackUpPosition() {
    // given
    final int numEventsBeforeBackUp = 10;
    final int numEventsInSnapshotAfterBackup = 5;

    final long backupPosition = writerServer.writeEvents(numEventsBeforeBackUp, EVENT);
    final long snapshotPosition = writerServer.writeEvents(numEventsInSnapshotAfterBackup, EVENT);
    replicatorSnapshotController.takeSnapshot(snapshotPosition);

    final DefaultRestoreInfoResponse defaultRestoreInfoResponse = new DefaultRestoreInfoResponse();
    defaultRestoreInfoResponse.setReplicationTarget(ReplicationTarget.SNAPSHOT);
    restoreClient.setRestoreInfoResponse(defaultRestoreInfoResponse);

    snapshotRestoreContext.setProcessorPositionSupplier(() -> snapshotPosition);
    snapshotRestoreContext.setExporterPositionSupplier(() -> snapshotPosition);

    // when
    distributedLog.restore(backupPosition);

    // then
    assertThat(readerClient.readEvents().size()).isEqualTo(1);
    assertThat(
            Arrays.stream(receiverStorage.getSnapshotsDirectory().listFiles())
                .anyMatch(f -> f.getName().equals(String.valueOf(snapshotPosition))))
        .isTrue();
  }

  @Test
  public void shouldRestoreFromLatestExportedPosition() {
    // given
    final int numEventsExported = 5;
    final int numEventsNotExported = 10;

    final long exporterPosition = writerServer.writeEvents(numEventsExported, EVENT);
    final long backupPosition = writerServer.writeEvents(numEventsNotExported, EVENT);
    replicatorSnapshotController.takeSnapshot(backupPosition);

    final DefaultRestoreInfoResponse defaultRestoreInfoResponse = new DefaultRestoreInfoResponse();
    defaultRestoreInfoResponse.setReplicationTarget(ReplicationTarget.SNAPSHOT);
    restoreClient.setRestoreInfoResponse(defaultRestoreInfoResponse);

    snapshotRestoreContext.setProcessorPositionSupplier(() -> backupPosition);
    snapshotRestoreContext.setExporterPositionSupplier(() -> exporterPosition);

    // when
    distributedLog.restore(backupPosition);

    // then
    assertThat(readerClient.readEvents().size()).isEqualTo(1 + numEventsNotExported);
  }

  @Test
  public void shouldRestoreEventsBetweenExporterAndSnapshotPosition() {
    // given
    final int numEventsExported = 5;
    final int numEventsNotExportedInBackup = 10;
    final int numEventsNotExportedAfterBackup = 2;

    final long exporterPosition = writerServer.writeEvents(numEventsExported, EVENT);
    final long backupPosition = writerServer.writeEvents(numEventsNotExportedInBackup, EVENT);
    final long snapshotPosition = writerServer.writeEvents(numEventsNotExportedAfterBackup, EVENT);
    replicatorSnapshotController.takeSnapshot(snapshotPosition);

    final DefaultRestoreInfoResponse defaultRestoreInfoResponse = new DefaultRestoreInfoResponse();
    defaultRestoreInfoResponse.setReplicationTarget(ReplicationTarget.SNAPSHOT);
    restoreClient.setRestoreInfoResponse(defaultRestoreInfoResponse);

    snapshotRestoreContext.setProcessorPositionSupplier(() -> snapshotPosition);
    snapshotRestoreContext.setExporterPositionSupplier(() -> exporterPosition);

    // when
    distributedLog.restore(backupPosition);

    readerServer.readEvents();
    // then
    assertThat(readerClient.readEvents().size())
        .isEqualTo(1 + numEventsNotExportedInBackup + numEventsNotExportedAfterBackup);
  }

  protected static final class Replicator implements SnapshotReplication {

    final List<SnapshotChunk> replicatedChunks = new ArrayList<>();
    private Consumer<SnapshotChunk> chunkConsumer;

    @Override
    public void replicate(SnapshotChunk snapshot) {
      replicatedChunks.add(snapshot);
      if (chunkConsumer != null) {
        chunkConsumer.accept(snapshot);
      }
    }

    @Override
    public void consume(Consumer<SnapshotChunk> consumer) {
      chunkConsumer = consumer;
    }

    @Override
    public void close() {}
  }
}

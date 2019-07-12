/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.distributedlog.restore.impl;

import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.assertj.core.api.Java6Assertions.assertThatThrownBy;

import io.atomix.utils.concurrent.SingleThreadContext;
import io.atomix.utils.concurrent.ThreadContext;
import io.zeebe.db.impl.DefaultColumnFamily;
import io.zeebe.db.impl.rocksdb.ZeebeRocksDbFactory;
import io.zeebe.distributedlog.impl.LogstreamConfig;
import io.zeebe.distributedlog.restore.RestoreInfoResponse.ReplicationTarget;
import io.zeebe.distributedlog.restore.RestoreNodeProvider;
import io.zeebe.distributedlog.restore.log.LogReplicator;
import io.zeebe.distributedlog.restore.snapshot.RestoreSnapshotReplicator;
import io.zeebe.logstreams.state.FileSnapshotConsumer;
import io.zeebe.logstreams.state.StateSnapshotController;
import io.zeebe.logstreams.state.StateStorage;
import io.zeebe.logstreams.util.LogStreamReaderRule;
import io.zeebe.logstreams.util.LogStreamRule;
import io.zeebe.logstreams.util.LogStreamWriterRule;
import io.zeebe.logstreams.util.RocksDBWrapper;
import io.zeebe.util.collection.Tuple;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import org.agrona.DirectBuffer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RestoreControllerTest {

  private static final int VALUE = 0xCAFE;
  private static final String KEY = "test";
  private static final DirectBuffer EVENT = wrapString("FOO");

  private final TemporaryFolder temporaryFolderClient = new TemporaryFolder();
  private final LogStreamRule logStreamRuleClient = new LogStreamRule(temporaryFolderClient);
  private final LogStreamWriterRule writerClient = new LogStreamWriterRule(logStreamRuleClient);
  private final LogStreamReaderRule readerClient = new LogStreamReaderRule(logStreamRuleClient);

  private final TemporaryFolder temporaryFolderServer = new TemporaryFolder();
  private final LogStreamRule logStreamRuleServer = new LogStreamRule(temporaryFolderServer);
  private final LogStreamWriterRule writerServer = new LogStreamWriterRule(logStreamRuleServer);
  private final LogStreamReaderRule readerServer = new LogStreamReaderRule(logStreamRuleServer);

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
  private RestoreController restoreController;

  @Before
  public void setUp() throws IOException {
    final File runtimeDirectory = temporaryFolderServer.newFolder("runtime");
    final File snapshotsDirectory = temporaryFolderServer.newFolder("snapshots");
    final StateStorage storage = new StateStorage(runtimeDirectory, snapshotsDirectory);
    final RocksDBWrapper wrapper = new RocksDBWrapper();
    final File receiverRuntimeDirectory = temporaryFolderClient.newFolder("runtime-receiver");
    final File receiverSnapshotsDirectory = temporaryFolderClient.newFolder("snapshots-receiver");

    replicatorSnapshotController =
        new StateSnapshotController(
            ZeebeRocksDbFactory.newFactory(DefaultColumnFamily.class), storage, null, 1);
    wrapper.wrap(replicatorSnapshotController.openDb());
    wrapper.putInt(KEY, VALUE);

    receiverStorage = new StateStorage(receiverRuntimeDirectory, receiverSnapshotsDirectory);
    snapshotRestoreContext = new ControllableSnapshotRestoreContext();
    snapshotRestoreContext.setProcessorStateStorage(receiverStorage);

    restoreClient =
        new ReplicatingRestoreClient(
            replicatorSnapshotController, logStreamRuleServer.getLogStream());
    restoreController = createRestoreController();
  }

  private RestoreController createRestoreController() {
    final ReplicatingRestoreClientProvider provider =
        new ReplicatingRestoreClientProvider(restoreClient, snapshotRestoreContext);
    final String clientNodeId = "0";
    LogstreamConfig.putRestoreFactory(clientNodeId, provider);

    final RestoreNodeProvider nodeProvider = provider.createNodeProvider(1);
    final ThreadContext restoreThreadContext = new SingleThreadContext("test");
    final Logger log = LoggerFactory.getLogger("test");

    final LogReplicator logReplicator =
        new LogReplicator(
            (commitPosition, blockBuffer) -> {
              logStreamRuleClient.getLogStream().setCommitPosition(commitPosition);
              try {
                return logStreamRuleClient
                    .getLogStream()
                    .getLogStorage()
                    .append(ByteBuffer.wrap(blockBuffer));
              } catch (IOException e) {
                throw new RuntimeException(e);
              }
            },
            restoreClient,
            restoreThreadContext,
            log);

    final RestoreSnapshotReplicator snapshotReplicator =
        new RestoreSnapshotReplicator(
            restoreClient,
            snapshotRestoreContext,
            new FileSnapshotConsumer(receiverStorage, log),
            restoreThreadContext,
            log);
    return new RestoreController(
        restoreClient, nodeProvider, logReplicator, snapshotReplicator, restoreThreadContext, log);
  }

  @Test
  public void shouldRestoreFromLogEvents() {
    // given
    final int numEventsInBackup = 10;
    final int numExtraEvents = 5;

    final long backupPosition = writerServer.writeEvents(numEventsInBackup, EVENT);
    writerServer.writeEvents(numExtraEvents, EVENT);

    // when
    final long restoredPosition = restoreController.restore(-1, backupPosition);

    // then
    assertThat(restoredPosition).isEqualTo(backupPosition);
    readerClient.assertEvents(numEventsInBackup, EVENT);
  }

  @Test
  public void shouldRestoreFromSnapshotAtBackUpPosition() {
    final int numEventsInSnapshot = 10;

    final long snapshotPosition = writerServer.writeEvents(numEventsInSnapshot, EVENT);
    replicatorSnapshotController.takeSnapshot(snapshotPosition);

    snapshotRestoreContext.setPositionSupplier(
        () -> new Tuple<>(snapshotPosition, snapshotPosition));

    final long restoredPosition = restoreController.restore(-1, snapshotPosition);

    assertThat(restoredPosition).isEqualTo(snapshotPosition);
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
    snapshotRestoreContext.setPositionSupplier(
        () -> new Tuple<>(snapshotPosition, snapshotPosition));
    // when
    final long restoredPosition = restoreController.restore(-1, backupPosition);

    // then
    assertThat(restoredPosition).isEqualTo(backupPosition);
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
    snapshotRestoreContext.setPositionSupplier(
        () -> new Tuple<>(snapshotPosition, snapshotPosition));

    // when
    final long restoredPosition = restoreController.restore(-1, backupPosition);

    // then
    assertThat(restoredPosition).isEqualTo(snapshotPosition);
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

    snapshotRestoreContext.setPositionSupplier(() -> new Tuple<>(exporterPosition, backupPosition));

    // when
    restoreController.restore(-1, backupPosition);

    // then
    assertThat(readerClient.readEvents().size()).isEqualTo(1 + numEventsNotExported);
  }

  @Test
  public void shouldRestoreFromLatestProcessedPositionIfNoExporters() {
    // given
    final int numEventsNotExported = 10;
    final long backupPosition = writerServer.writeEvents(numEventsNotExported, EVENT);
    replicatorSnapshotController.takeSnapshot(backupPosition);
    snapshotRestoreContext.setPositionSupplier(() -> new Tuple<>(Long.MAX_VALUE, backupPosition));

    // when
    restoreController.restore(-1, backupPosition);

    // then
    assertThat(readerClient.readEvents().size()).isEqualTo(1);
  }

  @Test
  public void shouldRestoreFromStartIfNoEventExported() {
    // given
    final int numEvents = 10;
    final long backupPosition = writerServer.writeEvents(numEvents, EVENT);
    replicatorSnapshotController.takeSnapshot(backupPosition);
    snapshotRestoreContext.setPositionSupplier(() -> new Tuple<>(-1L, backupPosition));

    // when
    restoreController.restore(-1, backupPosition);

    // then
    assertThat(readerClient.readEvents().size()).isEqualTo(numEvents);
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
    snapshotRestoreContext.setPositionSupplier(
        () -> new Tuple<>(exporterPosition, snapshotPosition));

    // when
    restoreController.restore(-1, backupPosition);

    readerServer.readEvents();
    // then
    assertThat(readerClient.readEvents().size())
        .isEqualTo(1 + numEventsNotExportedInBackup + numEventsNotExportedAfterBackup);
  }

  @Test
  public void shouldThrowExceptionIfSnapshotReplicationFailed() {
    restoreClient.setFailSnapshotChunk(true);

    final long backupPosition = writerServer.writeEvents(10, EVENT);
    replicatorSnapshotController.takeSnapshot(backupPosition);

    assertThatThrownBy(() -> restoreController.restore(-1, 10)).isNotNull();
  }

  @Test
  public void shouldThrowExceptionIfRequestInfoFailed() {
    restoreClient.completeRestoreInfoResponse(new RuntimeException());
    assertThatThrownBy(() -> restoreController.restore(-1, 10)).isNotNull();
  }

  @Test
  public void shouldDoNothingIfServerHasNothingToReplicate() {
    // given
    restoreClient.completeRestoreInfoResponse(
        new DefaultRestoreInfoResponse(ReplicationTarget.NONE));

    // when
    final long position = restoreController.restore(-1, 10);

    // then
    assertThat(position).isEqualTo(-1);
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.impl;

import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ACTIVATE_ELEMENT;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_ACTIVATING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.ZeebeDbFactory;
import io.camunda.zeebe.db.impl.jdbc.MixedTransactionContext;
import io.camunda.zeebe.db.impl.jdbc.MixedZeebeDb;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.stream.api.RecordProcessorContext;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.util.DefaultZeebeDbFactory;
import io.camunda.zeebe.stream.util.RecordToWrite;
import io.camunda.zeebe.stream.util.Records;
import java.io.File;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * A relational engine state database keeps its changes across restarts, so the replay must tell the
 * transaction context which command's events it applies before applying them: the stores then leave
 * the rows alone that the database already contains in a later state.
 */
final class StreamProcessorReplayCommittedChangesTest {

  private final MixedDbFactory dbFactory = new MixedDbFactory();

  @RegisterExtension
  private final StreamPlatformExtension streamPlatformExtension =
      new StreamPlatformExtension(dbFactory);

  @SuppressWarnings("unused") // injected by the extension
  private StreamPlatform streamPlatform;

  @Test
  void shouldTellTheContextBeforeApplyingWhetherTheDatabaseContainsTheReplayedChanges() {
    // given - the database contains the changes of the first command, but not of the second
    final long firstBatchPosition =
        streamPlatform.writeBatch(
            RecordToWrite.command().processInstance(ACTIVATE_ELEMENT, Records.processInstance(1)),
            RecordToWrite.event()
                .processInstance(ELEMENT_ACTIVATING, Records.processInstance(1))
                .causedBy(0));
    streamPlatform.writeBatch(
        RecordToWrite.command().processInstance(ACTIVATE_ELEMENT, Records.processInstance(2)),
        RecordToWrite.event()
            .processInstance(ELEMENT_ACTIVATING, Records.processInstance(2))
            .causedBy(0));
    dbFactory.committedPosition = firstBatchPosition;

    final List<Boolean> replayingCommittedChanges = new CopyOnWriteArrayList<>();
    final var recordProcessor = streamPlatform.getDefaultMockedRecordProcessor();
    final var processorContext = new RecordProcessorContext[1];
    doAnswer(
            invocation -> {
              processorContext[0] = invocation.getArgument(0);
              return null;
            })
        .when(recordProcessor)
        .init(any());
    doAnswer(
            invocation -> {
              final var context =
                  (MixedTransactionContext) processorContext[0].getTransactionContext();
              replayingCommittedChanges.add(context.jdbc().isReplayingCommittedChanges());
              return null;
            })
        .when(recordProcessor)
        .replay(any(TypedRecord.class));

    // when
    streamPlatform.startStreamProcessor();

    // then
    Awaitility.await("both events are replayed")
        .untilAsserted(() -> assertThat(replayingCommittedChanges).containsExactly(true, false));
  }

  private static final class MixedDbFactory implements ZeebeDbFactory<ZbColumnFamilies> {

    private final ZeebeDbFactory<ZbColumnFamilies> rocksDbFactory =
        DefaultZeebeDbFactory.defaultFactory();
    private volatile long committedPosition = -1;

    @Override
    public ZeebeDb<ZbColumnFamilies> createDb(final File pathName, final boolean avoidFlush) {
      return new MixedZeebeDb<>(rocksDbFactory.createDb(pathName, avoidFlush), committedPosition);
    }

    @Override
    public ZeebeDb<ZbColumnFamilies> createDb(final File pathName) {
      return new MixedZeebeDb<>(rocksDbFactory.createDb(pathName), committedPosition);
    }

    @Override
    public ZeebeDb<ZbColumnFamilies> openSnapshotOnlyDb(final File path) {
      return rocksDbFactory.openSnapshotOnlyDb(path);
    }
  }
}

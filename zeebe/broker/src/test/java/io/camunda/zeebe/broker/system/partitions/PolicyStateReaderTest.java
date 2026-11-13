/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.partitions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.broker.system.partitions.PolicyStateReader.UpdateStatus;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.engine.state.DefaultZeebeDbFactory;
import io.camunda.zeebe.engine.state.immutable.PolicyState.CommittedPolicy;
import io.camunda.zeebe.engine.state.policy.DbPolicyState;
import io.camunda.zeebe.engine.state.policy.DbPolicyUpdateState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.policyupdate.PolicyUpdateRecord;
import io.camunda.zeebe.scheduler.testing.TestConcurrencyControl;
import java.io.File;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class PolicyStateReaderTest {

  private final PartitionAdminControl adminControl = mock(PartitionAdminControl.class);
  private final PolicyStateReader reader =
      new PolicyStateReader(new TestConcurrencyControl(), adminControl);

  @TempDir private File dbDirectory;
  @TempDir private File otherDbDirectory;
  private ZeebeDb<ZbColumnFamilies> zeebeDb;
  private ZeebeDb<ZbColumnFamilies> otherZeebeDb;

  @AfterEach
  void tearDown() throws Exception {
    if (zeebeDb != null) {
      zeebeDb.close();
    }
    if (otherZeebeDb != null) {
      otherZeebeDb.close();
    }
  }

  @Test
  void shouldReadNothingWithoutAnOpenState() {
    // given
    when(adminControl.getZeebeDb()).thenReturn(null);

    // when
    final var status = reader.getPolicyState().join();

    // then
    assertThat(status).isEmpty();
  }

  @Test
  void shouldReadNoPolicyAndNoOpenUpdateFromAnEmptyState() {
    // given
    zeebeDb = openDb(dbDirectory);

    // when
    final var status = reader.getPolicyState().join().orElseThrow();

    // then
    assertThat(status.committed()).isEmpty();
    assertThat(status.committedSeqNo()).isZero();
    assertThat(status.updateStatus()).isEqualTo(UpdateStatus.NONE);
    assertThat(status.hasOpenUpdate()).isFalse();
  }

  @Test
  void shouldReadTheCommittedPolicy() {
    // given
    zeebeDb = openDb(dbDirectory);
    commit(zeebeDb, "policy-1", 3, 42);

    // when
    final var status = reader.getPolicyState().join().orElseThrow();

    // then
    assertThat(status.committed()).contains(new CommittedPolicy("policy-1", 3, 42));
    assertThat(status.committedSeqNo()).isEqualTo(42);
    assertThat(status.hasOpenUpdate()).isFalse();
  }

  @Test
  void shouldReadAPendingUpdate() {
    // given
    zeebeDb = openDb(dbDirectory);
    buffer(zeebeDb, chunk("update-1", 0, 2));

    // when
    final var status = reader.getPolicyState().join().orElseThrow();

    // then
    assertThat(status.updateStatus()).isEqualTo(UpdateStatus.PENDING);
    assertThat(status.openUpdateId()).isEqualTo("update-1");
    assertThat(status.hasOpenUpdate()).isTrue();
  }

  @Test
  void shouldReadAnApplyingUpdate() {
    // given
    zeebeDb = openDb(dbDirectory);
    buffer(zeebeDb, chunk("update-1", 0, 1));

    // when
    final var status = reader.getPolicyState().join().orElseThrow();

    // then
    assertThat(status.updateStatus()).isEqualTo(UpdateStatus.APPLYING);
    assertThat(status.openUpdateId()).isEqualTo("update-1");
  }

  @Test
  void shouldReadFromTheStateThatIsOpenNow() {
    // given a first read on one state
    zeebeDb = openDb(dbDirectory);
    commit(zeebeDb, "policy-1", 1, 10);
    reader.getPolicyState().join();

    // when the partition opens another state, e.g. after installing a snapshot
    otherZeebeDb = openDb(otherDbDirectory);
    commit(otherZeebeDb, "policy-1", 2, 20);
    final var status = reader.getPolicyState().join().orElseThrow();

    // then
    assertThat(status.committedSeqNo()).isEqualTo(20);
  }

  private ZeebeDb<ZbColumnFamilies> openDb(final File directory) {
    final ZeebeDb<ZbColumnFamilies> db =
        DefaultZeebeDbFactory.<ZbColumnFamilies>defaultFactory().createDb(directory);
    when(adminControl.getZeebeDb()).thenReturn(db);
    return db;
  }

  private static void commit(
      final ZeebeDb<ZbColumnFamilies> db,
      final String policyId,
      final long policyVersion,
      final long seqNo) {
    final var context = db.createContext();
    final var state = new DbPolicyState(db, context);
    context.runInTransaction(() -> state.commit(policyId, policyVersion, seqNo));
  }

  private static void buffer(final ZeebeDb<ZbColumnFamilies> db, final PolicyUpdateRecord chunk) {
    final var context = db.createContext();
    final var state = new DbPolicyUpdateState(db, context);
    context.runInTransaction(() -> state.bufferChunk(chunk));
  }

  private static PolicyUpdateRecord chunk(
      final String updateId, final int chunkIndex, final int chunkCount) {
    return new PolicyUpdateRecord()
        .setPolicyId("policy-1")
        .setPolicyVersion(1)
        .setUpdateId(updateId)
        .setChunkIndex(chunkIndex)
        .setChunkCount(chunkCount);
  }
}

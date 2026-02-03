/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.migration.to_8_7;

import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.engine.state.distribution.DbDistributionState;
import io.camunda.zeebe.engine.state.distribution.DistributionQueue;
import io.camunda.zeebe.engine.state.migration.MigrationTaskContextImpl;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.clock.ClockRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.impl.record.value.distribution.CommandDistributionRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ClockIntent;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.stream.impl.ClusterContextImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
public class EnsureRetriableDeploymentDistributionMigrationTest {

  public static final String QUEUE_ID = DistributionQueue.DEPLOYMENT.getQueueId();
  final EnsureRetriableDeploymentDistributionMigration sut =
      new EnsureRetriableDeploymentDistributionMigration();

  private ZeebeDb<ZbColumnFamilies> zeebeDb;
  private MutableProcessingState processingState;
  private TransactionContext transactionContext;

  private DbDistributionState state;
  private DbDistributionMigrationState8dot7 migrationState;

  @BeforeEach
  void setup() {
    state = new DbDistributionState(zeebeDb, transactionContext);
    migrationState = new DbDistributionMigrationState8dot7(zeebeDb, transactionContext);
  }

  @Test
  void shouldNotRunMigrationIfNoPendingDistributions() {
    // given

    // when
    final var context = new MigrationTaskContextImpl(new ClusterContextImpl(1), processingState);
    final var needsToRun = sut.needsToRun(context);

    // then
    assertThat(needsToRun).isFalse();
  }

  @Test
  void shouldNotRunMigrationIfNoPendingDeploymentDistributions() {
    // given
    final var distributionKey = 123L;
    final var partitionId = 2;
    state.addCommandDistribution(distributionKey, createClockRecord());
    state.addPendingDistribution(distributionKey, partitionId);

    // when
    final var context = new MigrationTaskContextImpl(new ClusterContextImpl(1), processingState);
    final var needsToRun = sut.needsToRun(context);

    // then
    assertThat(needsToRun).isFalse();
  }

  @Test
  void shouldNotRunMigrationIfDeploymentDistributionIsRetriableAndQueued() {
    // given
    final var distributionKey = 123L;
    final var partitionId = 2;
    state.addCommandDistribution(distributionKey, createDeploymentCreateRecord());
    state.addPendingDistribution(distributionKey, partitionId);
    state.addRetriableDistribution(distributionKey, partitionId);
    state.enqueueCommandDistribution(QUEUE_ID, distributionKey, partitionId);

    // when
    final var context = new MigrationTaskContextImpl(new ClusterContextImpl(1), processingState);
    final var needsToRun = sut.needsToRun(context);

    // then
    assertThat(needsToRun).isFalse();
  }

  @Test
  void shouldRunMigrationIfDeploymentDistributionIsNotQueued() {
    // given
    final var distributionKey = 123L;
    final var partitionId = 2;
    state.addCommandDistribution(distributionKey, createDeploymentCreateRecord());
    state.addPendingDistribution(distributionKey, partitionId);
    state.addRetriableDistribution(distributionKey, partitionId);

    // when
    final var context = new MigrationTaskContextImpl(new ClusterContextImpl(1), processingState);
    final var needsToRun = sut.needsToRun(context);
    assertThat(needsToRun).isTrue();
    sut.runMigration(context);

    // then
    assertThat(state.hasRetriableDistribution(distributionKey, partitionId)).isTrue();
    assertThat(state.hasQueuedDistribution(QUEUE_ID, distributionKey, partitionId)).isTrue();
  }

  @Test
  void shouldRunMigrationIfDeploymentDistributionIsNotRetriable() {
    // given
    final var distributionKey = 123L;
    final var partitionId = 2;
    state.addCommandDistribution(distributionKey, createDeploymentCreateRecord());
    state.addPendingDistribution(distributionKey, partitionId);
    state.enqueueCommandDistribution(QUEUE_ID, distributionKey, partitionId);

    // when
    final var context = new MigrationTaskContextImpl(new ClusterContextImpl(1), processingState);
    final var needsToRun = sut.needsToRun(context);
    assertThat(needsToRun).isTrue();
    sut.runMigration(context);

    // then
    assertThat(state.hasRetriableDistribution(distributionKey, partitionId)).isTrue();
    assertThat(state.hasQueuedDistribution(QUEUE_ID, distributionKey, partitionId)).isTrue();
  }

  @Test
  void shouldRunMigrationIfSecondDeploymentOnSamePartitionIsNotQueued() {
    // given
    final var distributionKey = 123L;
    final var distributionKey2 = 456L;
    final var partitionId = 2;
    state.addCommandDistribution(distributionKey, createDeploymentCreateRecord());
    state.addPendingDistribution(distributionKey, partitionId);
    state.addRetriableDistribution(distributionKey, partitionId);
    state.enqueueCommandDistribution(QUEUE_ID, distributionKey, partitionId);

    state.addCommandDistribution(distributionKey2, createDeploymentCreateRecord());
    state.addPendingDistribution(distributionKey2, partitionId);

    // when
    final var context = new MigrationTaskContextImpl(new ClusterContextImpl(2), processingState);
    final var needsToRun = sut.needsToRun(context);
    assertThat(needsToRun).isTrue();
    sut.runMigration(context);

    // then
    assertThat(state.hasRetriableDistribution(distributionKey, partitionId)).isTrue();
    assertThat(state.hasQueuedDistribution(QUEUE_ID, distributionKey, partitionId)).isTrue();
    assertThat(state.hasRetriableDistribution(distributionKey2, partitionId)).isFalse();
    assertThat(state.hasQueuedDistribution(QUEUE_ID, distributionKey2, partitionId)).isTrue();
  }

  @Test
  void shouldRunMigrationIfOtherPartitionIsNotRetriableAndQueued() {
    // given
    final var distributionKey = 123L;
    final var partitionId1 = 2;
    final var partitionId2 = 3;
    state.addCommandDistribution(distributionKey, createDeploymentCreateRecord());
    state.addPendingDistribution(distributionKey, partitionId1);
    state.addRetriableDistribution(distributionKey, partitionId1);
    state.enqueueCommandDistribution(QUEUE_ID, distributionKey, partitionId1);

    state.addPendingDistribution(distributionKey, partitionId2);

    // when
    final var context = new MigrationTaskContextImpl(new ClusterContextImpl(2), processingState);
    final var needsToRun = sut.needsToRun(context);
    assertThat(needsToRun).isTrue();
    sut.runMigration(context);

    // then
    assertThat(state.hasRetriableDistribution(distributionKey, partitionId1)).isTrue();
    assertThat(state.hasQueuedDistribution(QUEUE_ID, distributionKey, partitionId1)).isTrue();
    assertThat(state.hasRetriableDistribution(distributionKey, partitionId2)).isTrue();
    assertThat(state.hasQueuedDistribution(QUEUE_ID, distributionKey, partitionId2)).isTrue();
  }

  private CommandDistributionRecord createDeploymentCreateRecord() {
    final var deploymentRecord = new DeploymentRecord();
    deploymentRecord
        .resources()
        .add()
        .setResourceName("my_first_bpmn.bpmn")
        .setResource(wrapString("This is the contents of the BPMN"));
    deploymentRecord
        .processesMetadata()
        .add()
        .setKey(123)
        .setVersion(1)
        .setBpmnProcessId("my_first_process")
        .setResourceName("my_first_bpmn.bpmn")
        .setChecksum(wrapString("sha1"));

    return new CommandDistributionRecord()
        .setPartitionId(1)
        .setQueueId(QUEUE_ID)
        .setValueType(ValueType.DEPLOYMENT)
        .setIntent(DeploymentIntent.CREATE)
        .setCommandValue(deploymentRecord);
  }

  private CommandDistributionRecord createClockRecord() {
    final var clockRecord = new ClockRecord().pinAt(1000L);

    return new CommandDistributionRecord()
        .setPartitionId(1)
        .setValueType(ValueType.CLOCK)
        .setIntent(ClockIntent.PIN)
        .setCommandValue(clockRecord);
  }
}

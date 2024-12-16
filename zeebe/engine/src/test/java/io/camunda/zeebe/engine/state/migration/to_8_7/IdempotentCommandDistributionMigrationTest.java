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
import io.camunda.zeebe.protocol.impl.record.value.resource.ResourceDeletionRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ClockIntent;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.protocol.record.intent.ResourceDeletionIntent;
import io.camunda.zeebe.stream.impl.ClusterContextImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
public class IdempotentCommandDistributionMigrationTest {

  final IdempotentCommandDistributionMigration sut = new IdempotentCommandDistributionMigration();

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
  void shouldMigratePendingDistributionsToQueue() {
    // given
    final var createDistributionKey = 1L;
    final var deleteDistributionKey = 2L;
    final var partitionId = 1;
    final var createRecord = createDeploymentCreateRecord();
    final var deleteRecord = createResourceDeletionDeleteRecord();

    state.addCommandDistribution(createDistributionKey, createRecord);
    state.addCommandDistribution(deleteDistributionKey, deleteRecord);
    state.addPendingDistribution(createDistributionKey, partitionId);
    state.addPendingDistribution(deleteDistributionKey, partitionId);
    state.addRetriableDistribution(createDistributionKey, partitionId);
    state.addRetriableDistribution(deleteDistributionKey, partitionId);

    // when
    final var context = new MigrationTaskContextImpl(new ClusterContextImpl(1), processingState);
    assertThat(sut.needsToRun(context)).isTrue();
    sut.runMigration(context);

    // then
    assertThat(state.hasPendingDistribution(createDistributionKey)).isTrue();
    assertThat(state.hasPendingDistribution(deleteDistributionKey)).isTrue();
    assertThat(state.hasRetriableDistribution(createDistributionKey)).isTrue();
    assertThat(state.hasRetriableDistribution(deleteDistributionKey)).isFalse();

    final var queueId = DistributionQueue.DEPLOYMENT.getQueueId();
    assertThat(state.getQueueIdForDistribution(createDistributionKey))
        .isPresent()
        .get()
        .isEqualTo(queueId);
    assertThat(state.getQueueIdForDistribution(deleteDistributionKey))
        .isPresent()
        .get()
        .isEqualTo(queueId);

    assertThat(state.getNextQueuedDistributionKey(queueId, partitionId))
        .isPresent()
        .get()
        .isEqualTo(createDistributionKey);
    state.removeQueuedDistribution(queueId, partitionId, createDistributionKey);
    assertThat(state.getNextQueuedDistributionKey(queueId, partitionId))
        .isPresent()
        .get()
        .isEqualTo(deleteDistributionKey);
  }

  @Test
  void shouldNotMigrateNonDeploymentOrResourceDeletionDistributions() {
    // given
    final var distributionKey = 1L;
    final var partitionId = 1;
    final var clockRecord = createClockRecord();

    state.addCommandDistribution(distributionKey, clockRecord);
    state.addPendingDistribution(distributionKey, partitionId);
    state.addRetriableDistribution(distributionKey, partitionId);

    // when
    final var context = new MigrationTaskContextImpl(new ClusterContextImpl(1), processingState);
    assertThat(sut.needsToRun(context)).isFalse();
    sut.runMigration(context);

    // then
    assertThat(state.hasPendingDistribution(distributionKey)).isTrue();
    assertThat(state.hasRetriableDistribution(distributionKey)).isTrue();
    assertThat(state.getQueueIdForDistribution(distributionKey)).isEmpty();
    assertThat(state.getNextQueuedDistributionKey(DistributionQueue.DEPLOYMENT.getQueueId(), 1))
        .isEmpty();
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
        .setValueType(ValueType.DEPLOYMENT)
        .setIntent(DeploymentIntent.CREATE)
        .setCommandValue(deploymentRecord);
  }

  private CommandDistributionRecord createResourceDeletionDeleteRecord() {
    final var deletionRecord = new ResourceDeletionRecord().setResourceKey(1L);

    return new CommandDistributionRecord()
        .setPartitionId(1)
        .setValueType(ValueType.RESOURCE_DELETION)
        .setIntent(ResourceDeletionIntent.DELETE)
        .setCommandValue(deletionRecord);
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

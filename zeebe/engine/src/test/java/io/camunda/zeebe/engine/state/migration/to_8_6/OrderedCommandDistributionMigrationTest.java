/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.migration.to_8_6;

import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.engine.state.distribution.DbDistributionState;
import io.camunda.zeebe.engine.state.migration.MigrationTaskContextImpl;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.impl.record.value.distribution.CommandDistributionRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.stream.impl.ClusterContextImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

public class OrderedCommandDistributionMigrationTest {

  @Nested
  @ExtendWith(ProcessingStateExtension.class)
  class MigrateOrderedCommandDistribution {

    final OrderedCommandDistributionMigration sut = new OrderedCommandDistributionMigration();

    private ZeebeDb<ZbColumnFamilies> zeebeDb;
    private MutableProcessingState processingState;
    private TransactionContext transactionContext;

    private DbDistributionState state;
    private DbDistributionMigrationState migrationState;

    @BeforeEach
    void setup() {
      state = new DbDistributionState(zeebeDb, transactionContext);
      migrationState = new DbDistributionMigrationState(zeebeDb, transactionContext);
    }

    @Test
    void shouldMigratePendingDistributionsToRetriableDistributions() {
      // given
      final var distributionKey = 1L;
      final var partitionId = 1;
      state.addCommandDistribution(distributionKey, createCommandDistributionRecord());
      state.addPendingDistribution(distributionKey, partitionId);

      // when
      sut.runMigration(new MigrationTaskContextImpl(new ClusterContextImpl(1), processingState));

      // then
      assertThat(state.hasPendingDistribution(distributionKey)).isTrue();
      assertThat(state.hasPendingDistribution(distributionKey, partitionId)).isTrue();
      assertThat(state.hasRetriableDistribution(distributionKey)).isTrue();
      assertThat(state.hasRetriableDistribution(distributionKey, partitionId)).isTrue();

      assertThat(migrationState.existsPendingDistribution(distributionKey, partitionId)).isTrue();
      assertThat(migrationState.existsRetriableDistribution(distributionKey, partitionId)).isTrue();
      assertThat(migrationState.existsPendingDistribution(distributionKey, 2)).isFalse();
      assertThat(migrationState.existsRetriableDistribution(distributionKey, 2)).isFalse();
    }

    private CommandDistributionRecord createCommandDistributionRecord() {
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
          .setQueueId("totally-random-queue-id")
          .setValueType(ValueType.DEPLOYMENT)
          .setIntent(DeploymentIntent.CREATE)
          .setCommandValue(deploymentRecord);
    }
  }
}

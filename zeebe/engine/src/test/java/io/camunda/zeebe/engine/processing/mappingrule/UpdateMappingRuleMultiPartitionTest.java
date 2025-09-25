/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.mappingrule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.tuple;

import io.camunda.zeebe.engine.common.state.distribution.DistributionQueue;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.CommandDistributionIntent;
import io.camunda.zeebe.protocol.record.intent.MappingRuleIntent;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
import java.util.UUID;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class UpdateMappingRuleMultiPartitionTest {

  private static final int PARTITION_COUNT = 3;

  @Rule public final EngineRule engine = EngineRule.multiplePartition(PARTITION_COUNT);
  @Rule public final TestWatcher testWatcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldDistributeMappingUpdateCommand() {
    // given
    final var claimName = UUID.randomUUID().toString();
    final var claimValue = UUID.randomUUID().toString();
    final var name = UUID.randomUUID().toString();
    final var id = UUID.randomUUID().toString();
    engine
        .mappingRule()
        .newMappingRule(id)
        .withClaimName(claimName)
        .withClaimValue(claimValue)
        .withName(name)
        .create();

    // when
    engine
        .mappingRule()
        .updateMappingRule(id)
        .withClaimName(claimName + "New")
        .withClaimValue(claimValue + "New")
        .withName(name + "New")
        .update();

    for (int partitionId = 2; partitionId < PARTITION_COUNT; partitionId++) {
      assertThat(
              RecordingExporter.mappingRuleRecords()
                  .withPartitionId(partitionId)
                  .skip(2)
                  .limit(record -> record.getIntent().equals(MappingRuleIntent.UPDATED))
                  .toList())
          .extracting(Record::getIntent)
          .containsExactly(MappingRuleIntent.UPDATE, MappingRuleIntent.UPDATED);
    }
  }

  @Test
  public void shouldDistributeInIdentityQueue() {
    // given
    final var claimName = UUID.randomUUID().toString();
    final var claimValue = UUID.randomUUID().toString();
    final var name = UUID.randomUUID().toString();
    final var id = UUID.randomUUID().toString();
    engine
        .mappingRule()
        .newMappingRule(id)
        .withClaimName(claimName)
        .withClaimValue(claimValue)
        .withName(name)
        .create();

    // when
    engine
        .mappingRule()
        .updateMappingRule(id)
        .withClaimName(claimName + "New")
        .withClaimValue(claimValue + "New")
        .withName(name + "New")
        .update();

    // then
    assertThat(
            RecordingExporter.commandDistributionRecords()
                .limitByCount(r -> r.getIntent().equals(CommandDistributionIntent.FINISHED), 1)
                .withIntent(CommandDistributionIntent.ENQUEUED))
        .extracting(r -> r.getValue().getQueueId())
        .containsOnly(DistributionQueue.IDENTITY.getQueueId());
  }

  @Test
  public void distributionShouldNotOvertakeOtherCommandsInSameQueue() {
    // given the role creation distribution is intercepted
    for (int partitionId = 2; partitionId <= PARTITION_COUNT; partitionId++) {
      engine.interceptInterPartitionIntent(partitionId, MappingRuleIntent.CREATE);
    }
    final var claimName = UUID.randomUUID().toString();
    final var claimValue = UUID.randomUUID().toString();
    final var name = UUID.randomUUID().toString();
    final var id = UUID.randomUUID().toString();
    engine
        .mappingRule()
        .newMappingRule(id)
        .withClaimName(claimName)
        .withClaimValue(claimValue)
        .withName(name)
        .create();
    engine
        .mappingRule()
        .updateMappingRule(id)
        .withClaimName(claimName + "New")
        .withClaimValue(claimValue + "New")
        .withName(name + "New")
        .update();

    // Increase time to trigger a redistribution
    engine.increaseTime(Duration.ofMinutes(1));

    // then
    assertThat(
            RecordingExporter.commandDistributionRecords(CommandDistributionIntent.FINISHED)
                .limit(2))
        .extracting(r -> r.getValue().getValueType(), r -> r.getValue().getIntent())
        .containsExactly(
            tuple(ValueType.MAPPING_RULE, MappingRuleIntent.CREATE),
            tuple(ValueType.MAPPING_RULE, MappingRuleIntent.UPDATE));
  }
}

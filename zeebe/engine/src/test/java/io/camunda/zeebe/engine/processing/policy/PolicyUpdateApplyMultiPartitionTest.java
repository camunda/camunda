/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.policy;

import static io.camunda.zeebe.engine.processing.policy.PolicyUpdateApplyTest.appliedRecord;
import static io.camunda.zeebe.engine.processing.policy.PolicyUpdateApplyTest.apply;
import static io.camunda.zeebe.engine.processing.policy.PolicyUpdateApplyTest.chunk;
import static io.camunda.zeebe.engine.processing.policy.PolicyUpdateApplyTest.uid;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.impl.record.value.authorization.RoleRecord;
import io.camunda.zeebe.protocol.record.intent.RoleIntent;
import io.camunda.zeebe.protocol.record.value.RoleRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;

public final class PolicyUpdateApplyMultiPartitionTest {

  private static final int PARTITION_COUNT = 3;

  @Rule public final EngineRule engine = EngineRule.multiplePartition(PARTITION_COUNT);

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldApplyTheUpdateOnEveryPartition() {
    // given
    final var roleId = "role-" + uid();
    final var chunk =
        chunk()
            .setRoles(List.<RoleRecordValue>of(new RoleRecord().setRoleId(roleId).setName(roleId)));

    // when
    apply(engine, chunk);

    // then
    appliedRecord(chunk.getUpdateId());
    for (int partitionId = 1; partitionId <= PARTITION_COUNT; partitionId++) {
      assertThat(
              RecordingExporter.roleRecords(RoleIntent.CREATED)
                  .withRoleId(roleId)
                  .withPartitionId(partitionId)
                  .exists())
          .describedAs("role created on partition %d", partitionId)
          .isTrue();
    }
  }
}

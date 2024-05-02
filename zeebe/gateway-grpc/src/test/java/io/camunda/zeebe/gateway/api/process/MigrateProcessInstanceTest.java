/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.api.process;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.gateway.RequestMapper;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.MigrateProcessInstanceRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.MigrateProcessInstanceRequest.MappingInstruction;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.MigrateProcessInstanceRequest.MigrationPlan;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceMigrationRecord;
import org.junit.jupiter.api.Test;

public class MigrateProcessInstanceTest {

  @Test
  public void shouldMapRequestToMigrateProcessInstanceRequest() {
    // given
    final var request =
        MigrateProcessInstanceRequest.newBuilder()
            .setProcessInstanceKey(1L)
            .setMigrationPlan(
                MigrationPlan.newBuilder()
                    .setTargetProcessDefinitionKey(2L)
                    .addMappingInstructions(
                        MappingInstruction.newBuilder()
                            .setSourceElementId("sourceElementId1")
                            .setTargetElementId("targetElementId1")
                            .build())
                    .build())
            .build();

    // when
    final ProcessInstanceMigrationRecord record =
        RequestMapper.toMigrateProcessInstanceRequest(request).getRequestWriter();

    // then
    assertThat(record.getProcessInstanceKey()).isEqualTo(1L);
    assertThat(record.getTargetProcessDefinitionKey()).isEqualTo(2L);
    final var mappingInstructions = record.getMappingInstructions();
    assertThat(mappingInstructions).hasSize(1);
    final var mappingInstruction = mappingInstructions.get(0);
    assertThat(mappingInstruction.getSourceElementId()).isEqualTo("sourceElementId1");
    assertThat(mappingInstruction.getTargetElementId()).isEqualTo("targetElementId1");
  }
}

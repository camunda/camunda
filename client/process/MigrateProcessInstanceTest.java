/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.client.process;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.client.api.command.MigrateProcessInstanceCommandStep1.MigrateProcessInstanceCommandFinalStep;
import io.camunda.zeebe.client.api.command.MigrationPlan;
import io.camunda.zeebe.client.util.ClientTest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.MigrateProcessInstanceRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.MigrateProcessInstanceRequest.MappingInstruction;
import org.assertj.core.api.Assertions;
import org.junit.Test;

public class MigrateProcessInstanceTest extends ClientTest {

  private static final Long PI_KEY = 1L;
  private static final Long PD_KEY = 2L;
  private static final String ELEMENT_ID_A = "elementId_A";
  private static final String ELEMENT_ID_B = "elementId_B";
  private static final String ELEMENT_ID_C = "elementId_C";
  private static final String ELEMENT_ID_D = "elementId_D";

  @Test
  public void shouldMigrateWithSingleMappingInstruction() {
    // given
    final MigrateProcessInstanceCommandFinalStep migrateProcessInstanceCommand =
        client
            .newMigrateProcessInstanceCommand(PI_KEY)
            .migrationPlan(PD_KEY)
            .addMappingInstruction(ELEMENT_ID_A, ELEMENT_ID_B);

    // when
    migrateProcessInstanceCommand.send().join();

    // then
    final MigrateProcessInstanceRequest request = gatewayService.getLastRequest();
    assertRequest(request, 1);
    final MappingInstruction mappingInstruction1 =
        request.getMigrationPlan().getMappingInstructions(0);
    assertMappingInstruction(mappingInstruction1, ELEMENT_ID_A, ELEMENT_ID_B);
  }

  @Test
  public void shouldMigrateWithMultipleMappingInstructions() {
    // given
    final MigrateProcessInstanceCommandFinalStep migrateProcessInstanceCommand =
        client
            .newMigrateProcessInstanceCommand(PI_KEY)
            .migrationPlan(PD_KEY)
            .addMappingInstruction(ELEMENT_ID_A, ELEMENT_ID_B)
            .addMappingInstruction(ELEMENT_ID_C, ELEMENT_ID_D);

    // when
    migrateProcessInstanceCommand.send().join();

    // then
    final MigrateProcessInstanceRequest request = gatewayService.getLastRequest();
    assertRequest(request, 2);
    final MappingInstruction mappingInstruction1 =
        request.getMigrationPlan().getMappingInstructions(0);
    assertMappingInstruction(mappingInstruction1, ELEMENT_ID_A, ELEMENT_ID_B);
    final MappingInstruction mappingInstruction2 =
        request.getMigrationPlan().getMappingInstructions(1);
    assertMappingInstruction(mappingInstruction2, ELEMENT_ID_C, ELEMENT_ID_D);
  }

  @Test
  public void shouldMigrateWithMigrationPlanBuilder() {
    // given
    final MigrationPlan migrationPlan =
        MigrationPlan.newBuilder()
            .withTargetProcessDefinitionKey(PD_KEY)
            .addMappingInstruction(ELEMENT_ID_A, ELEMENT_ID_B)
            .addMappingInstruction(ELEMENT_ID_C, ELEMENT_ID_D)
            .build();

    final MigrateProcessInstanceCommandFinalStep migrateProcessInstanceCommand =
        client.newMigrateProcessInstanceCommand(PI_KEY).migrationPlan(migrationPlan);

    // when
    migrateProcessInstanceCommand.send().join();

    // then
    final MigrateProcessInstanceRequest request = gatewayService.getLastRequest();
    assertRequest(request, 2);
    final MappingInstruction mappingInstruction1 =
        request.getMigrationPlan().getMappingInstructions(0);
    assertMappingInstruction(mappingInstruction1, ELEMENT_ID_A, ELEMENT_ID_B);
    final MappingInstruction mappingInstruction2 =
        request.getMigrationPlan().getMappingInstructions(1);
    assertMappingInstruction(mappingInstruction2, ELEMENT_ID_C, ELEMENT_ID_D);
  }

  @Test
  public void shouldMigrateWithMigrationPlanBuilderAndAdditionalMappingInstruction() {
    // given
    final MigrationPlan migrationPlan =
        MigrationPlan.newBuilder()
            .withTargetProcessDefinitionKey(PD_KEY)
            .addMappingInstruction(ELEMENT_ID_A, ELEMENT_ID_B)
            .build();

    final MigrateProcessInstanceCommandFinalStep migrateProcessInstanceCommand =
        client
            .newMigrateProcessInstanceCommand(PI_KEY)
            .migrationPlan(migrationPlan)
            .addMappingInstruction(ELEMENT_ID_C, ELEMENT_ID_D);

    // when
    migrateProcessInstanceCommand.send().join();

    // then
    final MigrateProcessInstanceRequest request = gatewayService.getLastRequest();
    assertRequest(request, 2);
    final MappingInstruction mappingInstruction1 =
        request.getMigrationPlan().getMappingInstructions(0);
    assertMappingInstruction(mappingInstruction1, ELEMENT_ID_A, ELEMENT_ID_B);
    final MappingInstruction mappingInstruction2 =
        request.getMigrationPlan().getMappingInstructions(1);
    assertMappingInstruction(mappingInstruction2, ELEMENT_ID_C, ELEMENT_ID_D);
  }

  @Test
  public void shouldThrowErrorWhenTryToMigrateProcessInstanceWithNullMappingInstruction() {
    // when
    Assertions.assertThatThrownBy(
            () ->
                client
                    .newMigrateProcessInstanceCommand(PI_KEY)
                    .migrationPlan(PD_KEY)
                    .addMappingInstruction(null, null)
                    .send()
                    .join())
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void
      shouldThrowErrorWhenTryToMigrateProcessInstanceWithNullMappingInstructionInMigrationPlan() {
    // when
    Assertions.assertThatThrownBy(
            () -> {
              final MigrationPlan migrationPlan =
                  MigrationPlan.newBuilder()
                      .withTargetProcessDefinitionKey(PD_KEY)
                      .addMappingInstruction(null, null)
                      .build();

              client
                  .newMigrateProcessInstanceCommand(PI_KEY)
                  .migrationPlan(migrationPlan)
                  .send()
                  .join();
            })
        .isInstanceOf(IllegalArgumentException.class);
  }

  private void assertRequest(
      final MigrateProcessInstanceRequest request, final int expectedMappingInstructionsCount) {
    assertThat(request.getProcessInstanceKey()).isEqualTo(PI_KEY);
    assertThat(request.getMigrationPlan().getTargetProcessDefinitionKey()).isEqualTo(PD_KEY);
    assertThat(request.getMigrationPlan().getMappingInstructionsCount())
        .isEqualTo(expectedMappingInstructionsCount);
  }

  private void assertMappingInstruction(
      final MappingInstruction mappingInstruction,
      final String expectedSourceElementId,
      final String expectedTargetElementId) {
    assertThat(mappingInstruction.getSourceElementId()).isEqualTo(expectedSourceElementId);
    assertThat(mappingInstruction.getTargetElementId()).isEqualTo(expectedTargetElementId);
  }
}

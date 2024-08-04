/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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

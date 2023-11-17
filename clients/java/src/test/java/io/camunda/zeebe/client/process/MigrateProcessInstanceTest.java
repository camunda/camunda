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

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.camunda.zeebe.client.api.command.MigrateProcessInstanceCommandStep1.MigrateProcessInstanceCommandFinalStep;
import io.camunda.zeebe.client.api.command.MigrationPlan;
import io.camunda.zeebe.client.util.ClientTest;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.Test;

public class MigrateProcessInstanceTest extends ClientTest {

  private static final Long PI_KEY = 1L;
  private static final Long PD_KEY = 2L;
  private static final String ELEMENT_ID_A = "elementId_A";
  private static final String ELEMENT_ID_B = "elementId_B";
  private static final String ELEMENT_ID_C = "elementId_C";
  private static final String ELEMENT_ID_D = "elementId_D";

  @Test
  public void shouldMigrate() {
    // given
    final MigrateProcessInstanceCommandFinalStep migrateProcessInstanceCommand =
        client
            .newMigrateProcessInstanceCommand(PI_KEY)
            .migrationPlan(PD_KEY)
            .addMappingInstruction(ELEMENT_ID_A, ELEMENT_ID_B)
            .addMappingInstruction(ELEMENT_ID_C, ELEMENT_ID_D);

    // when
    final ThrowingCallable send = migrateProcessInstanceCommand::send;

    // then
    assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(send);
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
    final ThrowingCallable send = migrateProcessInstanceCommand::send;

    // then
    assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(send);
  }
}

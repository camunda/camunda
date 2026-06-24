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
package io.camunda.client.api.command;

import io.camunda.client.api.response.MigrateProcessInstanceResponse;

public interface MigrateProcessInstanceCommandStep1
    extends CommandWithCommunicationApiStep<MigrateProcessInstanceCommandStep1> {

  /**
   * Create a MigrationPlan {@link
   * io.camunda.zeebe.gateway.protocol.GatewayOuterClass.MigrateProcessInstanceRequest.MigrationPlan}
   * for the given target process definition key.
   *
   * @param targetProcessDefinitionKey the key of the target process definition
   * @return the builder for this command
   */
  MigrateProcessInstanceCommandStep2 migrationPlan(final long targetProcessDefinitionKey);

  /**
   * Use the provided MigrationPlan from the given {@link MigrationPlan} object.
   *
   * <p>Example MigrationPlan object creation:
   *
   * <pre>
   * final MigrationPlan migrationPlan =
   *         MigrationPlan.newBuilder()
   *             .withTargetProcessDefinitionKey(2L)
   *             .addMappingInstruction("element1", "element2")
   *             .addMappingInstruction("element3", "element4")
   *             .build();
   * </pre>
   *
   * @param migrationPlan the object that contains migration plan data
   * @return the builder for this command
   */
  MigrateProcessInstanceCommandFinalStep migrationPlan(final MigrationPlan migrationPlan);

  interface MigrateProcessInstanceCommandStep2 {
    /**
     * Add a {@link
     * io.camunda.zeebe.gateway.protocol.GatewayOuterClass.MigrateProcessInstanceRequest.MappingInstruction}
     * for the element that will be migrated and its target element id in the target process
     * definition.
     *
     * @param sourceElementId the element id to migrate from
     * @param targetElementId the element id to migrate into
     * @return the builder for this command
     */
    MigrateProcessInstanceCommandFinalStep addMappingInstruction(
        final String sourceElementId, final String targetElementId);
  }

  interface MigrateProcessInstanceCommandFinalStep
      extends MigrateProcessInstanceCommandStep2,
          CommandWithOperationReferenceStep<MigrateProcessInstanceCommandFinalStep>,
          FinalCommandStep<MigrateProcessInstanceResponse> {

    /**
     * Add a {@link
     * io.camunda.zeebe.gateway.protocol.GatewayOuterClass.MigrateProcessInstanceRequest.MappingInstruction}
     * for the element that will be migrated and its target element id in the target process
     * definition. This method allows to add more than one mapping instructions to the migration
     * plan.
     *
     * @param sourceElementId the element id to migrate from
     * @param targetElementId the element id to migrate into
     * @return the builder for this command
     */
    @Override
    MigrateProcessInstanceCommandFinalStep addMappingInstruction(
        final String sourceElementId, final String targetElementId);
  }
}

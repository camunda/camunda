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
package io.camunda.zeebe.client.api.command;

import io.camunda.zeebe.client.api.response.MigrateProcessInstanceResponse;

public interface MigrateProcessInstanceCommandStep1 {

  /**
   * Create an {@link
   * io.camunda.zeebe.gateway.protocol.GatewayOuterClass.MigrateProcessInstanceRequest.MigrationPlan}
   * for the given target process definition key. // TODO - Add description For this use {@link
   * #migrationPlan(long)}.
   *
   * @param targetProcessDefinitionKey the definition key of the target process
   * @return the builder for this command
   */
  MigrateProcessInstanceCommandStep2 migrationPlan(final long targetProcessDefinitionKey);

  FinalCommandStep<MigrateProcessInstanceResponse> migrationPlan(final MigrationPlan migrationPlan);

  interface MigrateProcessInstanceCommandStep2 {
    MigrateProcessInstanceCommandFinalStep withMappingInstruction(
        final String sourceElementId, final String targetElementId);
  }

  interface MigrateProcessInstanceCommandFinalStep
      extends MigrateProcessInstanceCommandStep2, FinalCommandStep<MigrateProcessInstanceResponse> {

    @Override
    MigrateProcessInstanceCommandFinalStep withMappingInstruction(
        final String sourceElementId, final String targetElementId);
  }
}

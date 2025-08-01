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

import io.camunda.zeebe.client.api.command.MigrationPlanBuilderImpl.MappingInstruction;
import java.util.List;

/**
 * @deprecated since 8.8 for removal in 8.10, replaced by {@link
 *     io.camunda.client.api.command.MigrationPlanImpl}
 */
@Deprecated
public final class MigrationPlanImpl implements MigrationPlan {

  final long targetProcessDefinitionKey;
  final List<MappingInstruction> mappingInstructions;

  public MigrationPlanImpl(
      final long targetProcessDefinitionKey, final List<MappingInstruction> mappingInstructions) {
    this.targetProcessDefinitionKey = targetProcessDefinitionKey;
    this.mappingInstructions = mappingInstructions;
  }

  @Override
  public long getTargetProcessDefinitionKey() {
    return targetProcessDefinitionKey;
  }

  @Override
  public List<MappingInstruction> getMappingInstructions() {
    return mappingInstructions;
  }
}

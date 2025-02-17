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

import io.camunda.zeebe.client.api.command.MigrationPlan.MigrationPlanBuilderFinalStep;
import io.camunda.zeebe.client.api.command.MigrationPlan.MigrationPlanBuilderStep1;
import io.camunda.zeebe.client.api.command.MigrationPlan.MigrationPlanBuilderStep2;
import java.util.ArrayList;
import java.util.List;

/**
 * @deprecated since 8.8 for removal in 8.9, replaced by {@link
 *     io.camunda.client.api.command.MigrationPlanBuilderImpl}
 */
@Deprecated
public final class MigrationPlanBuilderImpl
    implements MigrationPlanBuilderStep1, MigrationPlanBuilderStep2, MigrationPlanBuilderFinalStep {

  private long targetProcessDefinitionKey;
  private final List<MappingInstruction> mappingInstructions;

  public MigrationPlanBuilderImpl() {
    mappingInstructions = new ArrayList<>();
  }

  @Override
  public MigrationPlanBuilderStep2 withTargetProcessDefinitionKey(
      final long targetProcessDefinitionKey) {
    this.targetProcessDefinitionKey = targetProcessDefinitionKey;
    return this;
  }

  @Override
  public MigrationPlanBuilderFinalStep addMappingInstruction(
      final String sourceElementId, final String targetElementId) {
    mappingInstructions.add(new MappingInstruction(sourceElementId, targetElementId));
    return this;
  }

  @Override
  public MigrationPlan build() {
    return new MigrationPlanImpl(targetProcessDefinitionKey, mappingInstructions);
  }

  public static class MappingInstruction {
    private final String sourceElementId;
    private final String targetElementId;

    public MappingInstruction(final String sourceElementId, final String targetElementId) {
      this.sourceElementId = sourceElementId;
      this.targetElementId = targetElementId;
    }

    public String getSourceElementId() {
      return sourceElementId;
    }

    public String getTargetElementId() {
      return targetElementId;
    }
  }
}

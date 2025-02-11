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
 * @deprecated since 8.8 for removal in 8.9, replaced by {@link
 *     io.camunda.client.api.command.MigrationPlan}
 */
@Deprecated
public interface MigrationPlan {

  /** Create a new migration plan builder to build {@link MigrationPlan} object */
  static MigrationPlanBuilderStep1 newBuilder() {
    return new MigrationPlanBuilderImpl();
  }

  /**
   * Get the key of target process definition. targetProcessDefinitionKey indicates which process
   * definition to use for the migration.
   *
   * @return the target process definition key
   */
  public long getTargetProcessDefinitionKey();

  /**
   * Get mapping instructions to the migration for describing how to map elements from the source
   * process definition to the target process definition.
   *
   * @return list of mapping instructions
   */
  public List<MappingInstruction> getMappingInstructions();

  /**
   * @deprecated since 8.8 for removal in 8.9, replaced by {@link
   *     io.camunda.client.api.command.MigrationPlan.MigrationPlanBuilderStep1}
   */
  @Deprecated
  interface MigrationPlanBuilderStep1 {

    /**
     * Set the key of target process definition. targetProcessDefinitionKey indicates which process
     * definition to use for the migration.
     *
     * @return the next step of the builder
     */
    MigrationPlanBuilderStep2 withTargetProcessDefinitionKey(final long targetProcessDefinitionKey);
  }

  /**
   * @deprecated since 8.8 for removal in 8.9, replaced by {@link
   *     io.camunda.client.api.command.MigrationPlan.MigrationPlanBuilderStep2}
   */
  @Deprecated
  interface MigrationPlanBuilderStep2 {

    /**
     * Add a mapping instruction to the migration for describing how to map elements from the source
     * process definition to the target process definition.
     *
     * <p>For example, let's consider a source process definition with a service task with id {@code
     * "task1"} and the target process definition with a service task with id {@code "task2"}. The
     * mapping instruction could be:
     *
     * <pre>{@code
     * {
     *   "sourceElementId": "task1",
     *   "targetElementId": "task2"
     * }
     * }</pre>
     *
     * This mapping would migrate instances of the service task with id {@code "task1"} to the
     * service task with id {@code "task2"}.
     *
     * @param sourceElementId element to migrate
     * @param targetElementId element to migrate into
     * @return the next step of the builder
     */
    MigrationPlanBuilderFinalStep addMappingInstruction(
        final String sourceElementId, final String targetElementId);
  }

  /**
   * @deprecated since 8.8 for removal in 8.9, replaced by {@link
   *     io.camunda.client.api.command.MigrationPlan.MigrationPlanBuilderFinalStep}
   */
  @Deprecated
  interface MigrationPlanBuilderFinalStep extends MigrationPlanBuilderStep2 {

    /**
     * Build the {@link MigrationPlan} object after filling the object with migration data
     *
     * @return a reusable migration plan
     */
    MigrationPlan build();
  }
}

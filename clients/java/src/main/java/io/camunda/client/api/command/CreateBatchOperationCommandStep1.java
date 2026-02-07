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

import io.camunda.client.api.response.CreateBatchOperationResponse;
import io.camunda.client.api.search.filter.DecisionInstanceFilter;
import io.camunda.client.api.search.filter.ProcessInstanceFilter;
import io.camunda.client.api.search.request.TypedFilterableRequest.SearchRequestFilter;
import java.util.function.Consumer;

public interface CreateBatchOperationCommandStep1 {

  /**
   * Defines the type of the batch operation to cancel process instances.
   *
   * @return the builder for this command
   */
  CreateBatchOperationCommandStep2<ProcessInstanceFilter> processInstanceCancel();

  /**
   * Defines the type of the batch operation to delete process instances.
   *
   * @return the builder for this command
   */
  CreateBatchOperationCommandStep2<ProcessInstanceFilter> processInstanceDelete();

  /**
   * Defines the type of the batch operation to resolve incidents.
   *
   * @return the builder for this command
   */
  CreateBatchOperationCommandStep2<ProcessInstanceFilter> resolveIncident();

  /**
   * Defines the type of the batch operation to migrate process instances.
   *
   * @return the builder for this command
   */
  ProcessInstanceMigrationStep<ProcessInstanceFilter> migrateProcessInstance();

  /**
   * Defines the type of the batch operation to modify process instance.
   *
   * @return the builder for this command
   */
  ProcessInstanceModificationStep<ProcessInstanceFilter> modifyProcessInstance();

  /**
   * Defines the type of the batch operation to delete decision instances.
   *
   * @return the builder for this command
   */
  CreateBatchOperationCommandStep2<DecisionInstanceFilter> decisionInstanceDelete();

  interface CreateBatchOperationCommandStep2<E extends SearchRequestFilter> {

    /**
     * Sets the filter for the batch operation.
     *
     * @param filter the filter to use
     * @return the builder for fluent use
     */
    CreateBatchOperationCommandStep3<E> filter(E filter);

    /**
     * Sets the filter for the batch operation. Uses a consumer to modify the default filter.
     *
     * @param filter the filter to use
     * @return the builder for fluent use
     */
    CreateBatchOperationCommandStep3<E> filter(Consumer<E> filter);
  }

  interface ProcessInstanceMigrationStep<E extends SearchRequestFilter>
      extends CreateBatchOperationCommandStep2<E> {

    /**
     * Sets the whole migration plan for the batch operation. The migration plan contains the
     * detailed instructions how to migrate single active elements from the source process
     * definition to the target process definition.
     *
     * @param migrationPlan the migration plan to use for the batch operation
     * @return the builder for fluent use
     */
    ProcessInstanceMigrationStep<E> migrationPlan(MigrationPlan migrationPlan);

    /**
     * Adds a single migration mapping instruction to the command.
     *
     * @param sourceElementId the id of the source element to migrate
     * @param targetElementId the id of the target element to migrate into
     * @return the builder for fluent use
     */
    ProcessInstanceMigrationStep<E> addMappingInstruction(
        String sourceElementId, String targetElementId);

    /**
     * Sets the target process definition key for the migration.
     *
     * @param targetProcessDefinitionKey the key of the target process definition to migrate to
     * @return the builder for fluent use
     */
    ProcessInstanceMigrationStep<E> targetProcessDefinitionKey(long targetProcessDefinitionKey);
  }

  interface ProcessInstanceModificationStep<E extends SearchRequestFilter>
      extends CreateBatchOperationCommandStep2<E> {

    /**
     * Adds a move instruction to the command. These instructions will be applied to all matching
     * sourceElementIds and create matching new targetElementId's tokens on all processInstances
     * matching the filter.
     *
     * <p><b>Example: </b><br>
     * Given a process instance with the following structure:
     *
     * <pre>
     *   (start) -----> [ taskA ] -----> [ taskB ] ----> (end)
     * </pre>
     *
     * A running processInstance has an active element <code>taskA</code>.<br>
     * <br>
     * When the following move instructions are applied:
     *
     * <ul>
     *   <li>sourceElementId: <code>taskA</code>, targetElementId: <code>taskB</code>
     * </ul>
     *
     * Then <code>taskA</code> will be terminated and <code>taskB</code> will be activated.
     *
     * <ul>
     *   <li>move instructions, matching no active source element will have no effect on the
     *       processInstance
     *   <li>elements not matching any move instruction, will stay untouched
     *   <li>when the processInstance has more than one active instance of a sourceElement, all
     *       sourceElements will be terminated and for each terminated element a new targetElement
     *       will be activated
     * </ul>
     *
     * @return Returns a list of move instructions
     */
    ProcessInstanceModificationStep<E> addMoveInstruction(
        String sourceElementId, String targetElementId);
  }

  interface CreateBatchOperationCommandStep3<E extends SearchRequestFilter>
      extends FinalCommandStep<CreateBatchOperationResponse> {}
}

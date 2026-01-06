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
package io.camunda.zeebe.protocol.record.value;

import io.camunda.zeebe.protocol.record.ImmutableProtocol;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceMigrationRecordValue.ProcessInstanceMigrationMappingInstructionValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceModificationRecordValue.ProcessInstanceModificationMoveInstructionValue;
import java.util.List;
import org.immutables.value.Value;

/**
 * A record value that represents the creation of a batch operation. It contains the type of the
 * batch operation, a filter to specify the entities to operate on and optionally a migrationPlan
 * and a modification plan (depending on the type of the batch operation)
 */
@Value.Immutable
@ImmutableProtocol(builder = ImmutableBatchOperationCreationRecordValue.Builder.class)
public interface BatchOperationCreationRecordValue extends BatchOperationRelated, RecordValue {
  /**
   * @return batch operation type which defines the batch operation which should operate on the keys
   */
  BatchOperationType getBatchOperationType();

  /**
   * @return filter to apply in the batch operation to select the entities to operate on (as JSON)
   */
  String getEntityFilter();

  /**
   * @return the migration plan, this is only used for {@link
   *     BatchOperationType#MIGRATE_PROCESS_INSTANCE}
   */
  BatchOperationProcessInstanceMigrationPlanValue getMigrationPlan();

  /**
   * @return the modification plan, this is only used for {@link
   *     BatchOperationType#MODIFY_PROCESS_INSTANCE}
   */
  BatchOperationProcessInstanceModificationPlanValue getModificationPlan();

  /**
   * The list of partitions this batch operation is executed on. THis list will be filled by the
   * engine and is only available after the batch operation was created.
   *
   * @return the list of partitions
   */
  List<Integer> getPartitionIds();

  @Value.Immutable
  @ImmutableProtocol(
      builder = ImmutableBatchOperationProcessInstanceMigrationPlanValue.Builder.class)
  interface BatchOperationProcessInstanceMigrationPlanValue {

    /**
     * @return the key of the process definition to migrate to
     */
    long getTargetProcessDefinitionKey();

    /**
     * @return the mapping instructions, or an empty list if no instructions are available
     */
    List<ProcessInstanceMigrationMappingInstructionValue> getMappingInstructions();
  }

  @Value.Immutable
  @ImmutableProtocol(
      builder = ImmutableBatchOperationProcessInstanceModificationPlanValue.Builder.class)
  interface BatchOperationProcessInstanceModificationPlanValue {

    /**
     * A list of move instructions. These instructions will be applied to all matching
     * sourceElements and activate matching new targetElements.
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
    List<ProcessInstanceModificationMoveInstructionValue> getMoveInstructions();
  }
}

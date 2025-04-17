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
import java.util.List;
import org.immutables.value.Value;

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
   *     BatchOperationType#MODIFY_PROCESS_INSTANCE}
   */
  ProcessInstanceModificationPlanValue getModificationPlan();

  @Value.Immutable
  @ImmutableProtocol(builder = ImmutableProcessInstanceModificationPlanValue.Builder.class)
  interface ProcessInstanceModificationPlanValue {

    /**
     * A list of move instructions. These instructions will be applied to all matching
     * sourceElementIds and create matching new targetElementId's tokens and add specified global
     * variables.
     *
     * @return Returns a list of move instructions
     */
    List<ProcessInstanceModificationMoveInstructionValue> getMoveInstructions();
  }

  @Value.Immutable
  @ImmutableProtocol(
      builder = ImmutableProcessInstanceModificationMoveInstructionValue.Builder.class)
  interface ProcessInstanceModificationMoveInstructionValue {

    /** Returns the id of the element to create a new element instance at. */
    String getSourceElementId();

    /** Returns the id of the element to create a new element instance at. */
    String getTargetElementId();
  }
}

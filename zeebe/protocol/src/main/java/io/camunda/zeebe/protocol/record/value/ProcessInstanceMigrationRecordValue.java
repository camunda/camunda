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
@ImmutableProtocol(builder = ImmutableProcessInstanceMigrationRecordValue.Builder.class)
public interface ProcessInstanceMigrationRecordValue
    extends RecordValue, ProcessInstanceRelated, AuditLogProcessInstanceRelated, TenantOwned {

  /**
   * @return the key of the process definition to migrate to
   */
  long getTargetProcessDefinitionKey();

  /**
   * @return the mapping instructions, or an empty list if no instructions are available
   */
  List<ProcessInstanceMigrationMappingInstructionValue> getMappingInstructions();

  /**
   * Returns the key of the root process instance in the hierarchy. For top-level process instances,
   * this is equal to {@link #getProcessInstanceKey()}. For child process instances (created via
   * call activities), this is the key of the topmost parent process instance.
   *
   * <p>Important: This value is only set for process instance records created after version 8.9.0
   * and part of hierarchies created after that version. For older process instances, the method
   * will return -1.
   *
   * @return the key of the root process instance, or {@code -1} if not set
   */
  long getRootProcessInstanceKey();

  /**
   * Mapping instructions for the migration describe how to map elements from the source process
   * definition to the target process definition.
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
   * This mapping would migrate instances of the service task with id {@code "task1"} to the service
   * task with id {@code "task2"}.
   */
  @Value.Immutable
  @ImmutableProtocol(
      builder = ImmutableProcessInstanceMigrationMappingInstructionValue.Builder.class)
  interface ProcessInstanceMigrationMappingInstructionValue {

    /**
     * @return the source element id, or an empty string
     */
    String getSourceElementId();

    /**
     * @return the target element id, or an empty string
     */
    String getTargetElementId();
  }
}

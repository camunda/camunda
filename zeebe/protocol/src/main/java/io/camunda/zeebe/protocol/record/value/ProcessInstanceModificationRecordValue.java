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
import java.util.Map;
import java.util.Set;
import org.immutables.value.Value;

@Value.Immutable
@ImmutableProtocol(builder = ImmutableProcessInstanceModificationRecordValue.Builder.class)
public interface ProcessInstanceModificationRecordValue
    extends RecordValue, ProcessInstanceRelated, AuditLogProcessInstanceRelated, TenantOwned {

  /** Returns a list of terminate instructions (if available), or an empty list. */
  List<ProcessInstanceModificationTerminateInstructionValue> getTerminateInstructions();

  /** Returns a list of activate instructions (if available), or an empty list. */
  List<ProcessInstanceModificationActivateInstructionValue> getActivateInstructions();

  /** Returns a list of move instructions (if available), or an empty list. */
  List<ProcessInstanceModificationMoveInstructionValue> getMoveInstructions();

  /**
   * Returns a list of all ancestor keys of all activate instructions. The property is set in the
   * event only after the modification is applied.
   *
   * @deprecated since 8.1.3, replaced by {@link
   *     ProcessInstanceModificationActivateInstructionValue#getAncestorScopeKeys()}
   */
  @Deprecated
  Set<Long> getAncestorScopeKeys();

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

  @Value.Immutable
  @ImmutableProtocol(
      builder = ImmutableProcessInstanceModificationTerminateInstructionValue.Builder.class)
  interface ProcessInstanceModificationTerminateInstructionValue {

    /** Returns the key of element instance to terminate. */
    long getElementInstanceKey();

    /** Returns the id of the elements to terminate. */
    String getElementId();
  }

  @Value.Immutable
  @ImmutableProtocol(
      builder = ImmutableProcessInstanceModificationActivateInstructionValue.Builder.class)
  interface ProcessInstanceModificationActivateInstructionValue {

    /** Returns the id of the element to create a new element instance at. */
    String getElementId();

    /**
     * Returns the key of the ancestor scope to create the new element instance in, or -1 if no
     * specific ancestor is selected.
     *
     * <p>This key is used for ancestor selection:
     *
     * <p>By default, the new element instance is created within an existing element instance of the
     * flow scope. For example, when activating an element inside an embedded subprocess and the
     * subprocess is already active.
     *
     * <p>If there is more than one element instance of the flow scope active then the engine can't
     * decide which element instance to create the new element instance in. Instead, the element
     * instance must be selected by its element instance key. The new element instance is created
     * within the selected element instance.
     *
     * <p>If the selected element instance is not of the flow scope but from a higher scope (e.g.
     * the process instance key instead of the element instance key of the subprocess) then the
     * engine creates a new element instance of the flow scope first and then creates the new
     * element instance within this scope.
     */
    long getAncestorScopeKey();

    /** Returns a list of variable instructions (if available), or an empty list. */
    List<ProcessInstanceModificationVariableInstructionValue> getVariableInstructions();

    /**
     * Returns all ancestor scope keys of the element that will be activated. The property is set in
     * the event only after the modification is applied.
     */
    Set<Long> getAncestorScopeKeys();
  }

  @Value.Immutable
  @ImmutableProtocol(
      builder = ImmutableProcessInstanceModificationVariableInstructionValue.Builder.class)
  interface ProcessInstanceModificationVariableInstructionValue {

    /** Returns the variables of this instruction. Can be empty. */
    Map<String, Object> getVariables();

    /**
     * Returns the element id of the scope where the variables should be created in, or an empty
     * string if the variables are global for the process instance.
     */
    String getElementId();
  }

  @Value.Immutable
  @ImmutableProtocol(
      builder = ImmutableProcessInstanceModificationMoveInstructionValue.Builder.class)
  interface ProcessInstanceModificationMoveInstructionValue {

    /** Returns the id of the element to terminate element instances at. */
    String getSourceElementId();

    /** Returns the key of element instance to terminate. */
    long getSourceElementInstanceKey();

    /** Returns the id of the element to create a new element instance at. */
    String getTargetElementId();

    /** Returns a list of variable instructions (if available), or an empty list. */
    List<ProcessInstanceModificationVariableInstructionValue> getVariableInstructions();

    /** Returns the key of the ancestor scope to use. */
    long getAncestorScopeKey();

    /**
     * Indicates whether the ancestor scope key should be inferred from the source element's
     * hierarchy.
     */
    boolean isInferAncestorScopeFromSourceHierarchy();

    /**
     * Indicates whether the source's direct parent key should be used as the ancestor scope key for
     * the target element. This is a simpler alternative to {@link
     * #isInferAncestorScopeFromSourceHierarchy()} that skips hierarchy traversal and directly uses
     * the source's parent key.
     */
    boolean isUseSourceParentKeyAsAncestorScopeKey();
  }
}

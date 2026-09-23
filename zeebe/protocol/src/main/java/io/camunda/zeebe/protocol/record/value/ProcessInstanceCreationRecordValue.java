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
import io.camunda.zeebe.protocol.record.RecordValueWithVariables;
import java.util.List;
import java.util.Set;
import org.immutables.value.Value;

@Value.Immutable
@ImmutableProtocol(builder = ImmutableProcessInstanceCreationRecordValue.Builder.class)
public interface ProcessInstanceCreationRecordValue
    extends RecordValueWithVariables,
        ProcessInstanceRelated,
        AuditLogProcessInstanceRelated,
        TenantOwned {
  /**
   * @return the BPMN process id to create a process from
   */
  @Override
  String getBpmnProcessId();

  /**
   * @return the version of the BPMN process to create a process from
   */
  int getVersion();

  /**
   * @return the unique key of the BPMN process definition to create a process from
   */
  @Override
  long getProcessDefinitionKey();

  /** Returns a list of start instructions (if available), or an empty list. */
  List<ProcessInstanceCreationStartInstructionValue> getStartInstructions();

  /** Returns a list of runtime instructions (if available), or an empty list. */
  List<ProcessInstanceCreationRuntimeInstructionValue> getRuntimeInstructions();

  /** Returns a set of tags */
  Set<String> getTags();

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
   * Returns the business id for the process instance to be created. The business id is an
   * immutable, user-defined string identifier that uniquely identifies a process instance within
   * the scope of a process definition.
   *
   * <p>If provided, the engine will enforce uniqueness: only one active process instance with a
   * given business id can exist per process definition (scoped by tenant). If a process instance
   * with the same business id already exists, the creation will be rejected.
   *
   * @return the business id, or an empty string if not set
   * @since 8.9
   */
  String getBusinessId();

  /**
   * Returns whether the call activities of this process instance are stubbed. A stubbed call
   * activity starts no child process instance: it activates and waits on a job that a caller
   * completes, either supplying the result variables the called process would have produced, or
   * asking the engine to start the real child process after all.
   *
   * @return {@code true} if the call activities of this process instance are stubbed
   * @since 8.11
   */
  default boolean isStubCallActivities() {
    return false;
  }

  /**
   * Returns whether the timers of this process instance are held back from the scheduler. A held
   * timer does not fire on its due date; it fires only when triggered explicitly via the trigger
   * command. Holding a timer keeps it out of the due-date scheduler while leaving the instance
   * otherwise drivable.
   *
   * @return {@code true} if the timers of this process instance are held
   * @since 8.11
   */
  default boolean isHoldTimers() {
    return false;
  }

  @Value.Immutable
  @ImmutableProtocol(builder = ImmutableProcessInstanceCreationStartInstructionValue.Builder.class)
  interface ProcessInstanceCreationStartInstructionValue {
    String getElementId();
  }

  @Value.Immutable
  @ImmutableProtocol(
      builder = ImmutableProcessInstanceCreationRuntimeInstructionValue.Builder.class)
  interface ProcessInstanceCreationRuntimeInstructionValue {
    RuntimeInstructionType getType();

    String getAfterElementId();

    /**
     * Returns the token that reserves this process instance's jobs for the caller. Set on a {@link
     * RuntimeInstructionType#RESERVE_JOBS} instruction only. Reserved jobs are not handed to job
     * workers that activate normally, and their complete, fail and throw-error commands are
     * rejected unless the same token is supplied.
     *
     * @return the job reservation token, or an empty string if not set
     * @since 8.11
     */
    default String getJobReservationToken() {
      return "";
    }
  }
}

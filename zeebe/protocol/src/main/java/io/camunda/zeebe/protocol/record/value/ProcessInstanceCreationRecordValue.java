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
    extends RecordValueWithVariables, ProcessInstanceRelated, TenantOwned {
  /**
   * @return the BPMN process id to create a process from
   */
  String getBpmnProcessId();

  /**
   * @return the version of the BPMN process to create a process from
   */
  int getVersion();

  /**
   * @return the unique key of the BPMN process definition to create a process from
   */
  long getProcessDefinitionKey();

  /** Returns a list of start instructions (if available), or an empty list. */
  List<ProcessInstanceCreationStartInstructionValue> getStartInstructions();

  /** Returns a list of runtime instructions (if available), or an empty list. */
  List<ProcessInstanceCreationRuntimeInstructionValue> getRuntimeInstructions();

  /** Returns a set of tags */
  Set<String> getTags();

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
  }
}

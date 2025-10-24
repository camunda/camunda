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
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import java.util.List;
import java.util.Set;
import org.immutables.value.Value;

/**
 * Represents a process instance related command or event.
 *
 * <p>See {@link ProcessInstanceIntent} for intents.
 */
@Value.Immutable
@ImmutableProtocol(builder = ImmutableProcessInstanceRecordValue.Builder.class)
public interface ProcessInstanceRecordValue
    extends RecordValue, ProcessInstanceRelated, TenantOwned {
  /**
   * @return the BPMN process id this process instance belongs to.
   */
  String getBpmnProcessId();

  /**
   * @return the version of the deployed process this instance belongs to.
   */
  int getVersion();

  /**
   * @return the key of the deployed process this instance belongs to.
   */
  long getProcessDefinitionKey();

  /**
   * @return the key of the process instance
   */
  @Override
  long getProcessInstanceKey();

  /**
   * @return the id of the current process element, or empty if the id is not specified.
   */
  String getElementId();

  /**
   * @return the key of the activity instance that is the flow scope of this flow element instance;
   *     -1 for records of the process instance itself.
   */
  long getFlowScopeKey();

  /**
   * @return the BPMN type of the current process element.
   */
  BpmnElementType getBpmnElementType();

  /**
   * @return the key of the process instance that created this instance, or -1 if it was not created
   *     by another process instance.
   */
  long getParentProcessInstanceKey();

  /**
   * @return the key of the element instance that created this instance, or -1 if it was not created
   *     by another process instance.
   */
  long getParentElementInstanceKey();

  /**
   * @return the BPMN event type of the current process element.
   */
  BpmnEventType getBpmnEventType();

  /**
   * A multi-list of element instances.
   *
   * <p>It contains a list per process instance in the hierarchy, each contains all the instance
   * keys of all the elements in the call hierarchy within this process instance
   *
   * <p>Example:
   *
   * <p><b>Element in main process:
   *
   * <pre>
   * [ PI ROOT -> Element ]
   *
   * List:
   *  - 0:
   *    - PI ROOT key
   *    - Element Instance Key
   * </pre>
   *
   * <p><b>Element in sub process via two call activities:
   *
   * <pre>
   * [ PI ROOT -> Call Activity 1 -> PI SUB 1 -> Call activity 2 -> PI SUB 2 -> Element instance key]
   *
   * List:
   *   - 0:
   *     - PI ROOT key
   *     - Call activity 1 elements instance key
   *   - 1:
   *     - PI SUB 1 key
   *     - Call activity 2 elements instance key
   *   - 2:
   *     - PI SUB 2 key
   *     - Elements instance key
   * </pre>
   *
   * <p><b>Element in embedded sub process:
   *
   * <pre>
   * [ PI ROOT -> Embedded sub process 1 -> Element instance key]
   *
   * List:
   *   - 0:
   *     - PI ROOT key
   *     - Embedded sub process 1 element instance key
   *     - Elements instance key
   * </pre>
   *
   * <p><b>Element in double embedded sub process:
   *
   * <pre>
   * [ PI ROOT -> Embedded sub process 1 -> Embedded sub process 2 -> Element instance key]
   *
   * List:
   *   - 0:
   *     - PI ROOT key
   *     - Embedded sub process 1 element instance key
   *     - Embedded sub process 2 element instance key
   *     - Elements instance key
   * </pre>
   *
   * @return tree path information about all element instances in the call hierarchy
   */
  List<List<Long>> getElementInstancePath();

  /**
   * @return tree path information for all process definitions in the hierarchy. Each entry is a
   *     process definition key for the corresponding process instance
   */
  List<Long> getProcessDefinitionPath();

  /**
   * List of lexicographical ids of calling elements in BPMN Process models.
   *
   * Example:
   * <pre>
   * [ PI ROOT -> Call Activity 1 -> PI SUB 1 -> Call activity 2 -> PI SUB 2]
   * - Process Model of PI ROOT, contains 3 call activities. Call Activity 1 is the second.
   * - Process Model of PI SUB 1, contains 6 call activities. Call activity 2 is the fifth.
   *
   * Result (indices start by zero):
   *   - 0: 1
   *   - 1: 4
   * </p>
   *
   * @return tree path information about call activities in the hierarchy. Each entry is a reference
   *     to the call activity in BPMN model containing an incident.
   */
  List<Integer> getCallingElementPath();

  /**
   * @return a set of tags associated with this process instance
   */
  Set<String> getTags();
}

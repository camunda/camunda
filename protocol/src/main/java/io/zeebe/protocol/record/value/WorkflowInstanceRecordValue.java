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
package io.zeebe.protocol.record.value;

import io.zeebe.protocol.record.RecordValue;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;

/**
 * Represents a workflow instance related command or event.
 *
 * <p>See {@link WorkflowInstanceIntent} for intents.
 */
public interface WorkflowInstanceRecordValue extends RecordValue, WorkflowInstanceRelated {
  /** @return the BPMN process id this workflow instance belongs to. */
  String getBpmnProcessId();

  /** @return the version of the deployed workflow this instance belongs to. */
  int getVersion();

  /** @return the key of the deployed workflow this instance belongs to. */
  long getWorkflowKey();

  /** @return the key of the workflow instance */
  long getWorkflowInstanceKey();

  /** @return the id of the current workflow element, or empty if the id is not specified. */
  String getElementId();

  /**
   * @return the key of the activity instance that is the flow scope of this flow element instance;
   *     -1 for records of the workflow instance itself.
   */
  long getFlowScopeKey();

  /** @return the BPMN type of the current workflow element. */
  BpmnElementType getBpmnElementType();

  /**
   * @return the key of the workflow instance that created this instance, or -1 if it was not
   *     created by another workflow instance.
   */
  long getParentWorkflowInstanceKey();

  /**
   * @return the key of the element instance that created this instance, or -1 if it was not created
   *     by another workflow instance.
   */
  long getParentElementInstanceKey();
}

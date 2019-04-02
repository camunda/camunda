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
package io.zeebe.exporter.api.record.value;

import io.zeebe.exporter.api.record.RecordValue;
import io.zeebe.protocol.BpmnElementType;

/**
 * Represents a workflow instance related command or event.
 *
 * <p>See {@link io.zeebe.protocol.intent.WorkflowInstanceIntent} for intents.
 */
public interface WorkflowInstanceRecordValue extends RecordValue {
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
}

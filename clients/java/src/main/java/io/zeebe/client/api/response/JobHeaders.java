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
package io.zeebe.client.api.response;

/** Context in case the job is part of a workflow instance */
public interface JobHeaders {

  /** @return key of the workflow instance */
  long getWorkflowInstanceKey();

  /** @return BPMN process id of the workflow */
  String getBpmnProcessId();

  /** @return version of the workflow */
  int getWorkflowDefinitionVersion();

  /** @return key of the workflow */
  long getWorkflowKey();

  /** @return id of the workflow element */
  String getElementId();

  /** @return key of the element instance */
  long getElementInstanceKey();
}

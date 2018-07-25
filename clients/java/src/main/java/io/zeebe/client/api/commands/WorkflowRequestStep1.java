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
package io.zeebe.client.api.commands;

public interface WorkflowRequestStep1 extends FinalCommandStep<Workflows> {

  /**
   * Filter the workflows by the given BPMN process id . This is the static id of the process in the
   * BPMN XML (i.e. "&#60;bpmn:process id='my-workflow'&#62;").
   *
   * @param bpmnProcessId the BPMN process id of the workflows
   * @return the builder for this request
   */
  WorkflowRequestStep1 bpmnProcessId(String bpmnProcessId);
}

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
package io.camunda.client.api.search.response;

public interface ProcessInstanceSequenceFlow {

  /** ID of the sequence flow */
  String getSequenceFlowId();

  /** process instance key for the sequence flow instance */
  String getProcessInstanceKey();

  /**
   * Returns the key of the root process instance. The root process instance is the top-level
   * ancestor in the process instance hierarchy.
   *
   * <p><strong>Note:</strong> This field is {@code null} for process instance hierarchies created
   * before version 8.9.
   *
   * @return the root process instance key, or {@code null} for data created before version 8.9
   */
  String getRootProcessInstanceKey();

  /** process definition key for the sequence flow instance */
  String getProcessDefinitionKey();

  /** process definition id for the sequence flow instance */
  String getProcessDefinitionId();

  /** element id for the sequence flow instance */
  String getElementId();

  /** tenant id for the sequence flow instance */
  String getTenantId();
}

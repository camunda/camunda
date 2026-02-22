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
package io.camunda.client.api.response;

import java.util.Set;

public interface ProcessInstanceEvent {

  /** Key of the process which this instance was created for */
  long getProcessDefinitionKey();

  /** BPMN process id of the process which this instance was created for */
  String getBpmnProcessId();

  /** Version of the process which this instance was created for */
  int getVersion();

  /** Unique key of the created process instance on the partition */
  long getProcessInstanceKey();

  /** Tenant identifier that owns this process instance */
  String getTenantId();

  /** Tags attached to this process instance */
  Set<String> getTags();

  /**
   * The business id of this process instance.
   *
   * @return the business id, or an empty string if not set
   */
  String getBusinessId();
}

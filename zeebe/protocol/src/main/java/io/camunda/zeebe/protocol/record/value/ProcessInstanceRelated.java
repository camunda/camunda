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

public interface ProcessInstanceRelated {

  /**
   * @return the key of the corresponding process instance
   */
  long getProcessInstanceKey();

  long getProcessDefinitionKey();

  // TODO: https://github.com/camunda/camunda/issues/43422 step 2 - remove default implementation
  // and implement for missing classes
  default String getBpmnProcessId() {
    return "";
  }

  // TODO: https://github.com/camunda/camunda/issues/43422 step 3 - remove default implementation
  // and implement for missing classes
  default long getElementInstanceKey() {
    return -1L;
  }
}

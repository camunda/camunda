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
package io.camunda.client.wrappers;

public class IncidentResult {

  public enum State {
    ACTIVE,
    MIGRATED,
    RESOLVED,
    PENDING,
    UNKNOWN_ENUM_VALUE;
  }

  public enum ErrorType {
    UNSPECIFIED,
    UNKNOWN,
    IO_MAPPING_ERROR,
    JOB_NO_RETRIES,
    EXECUTION_LISTENER_NO_RETRIES,
    TASK_LISTENER_NO_RETRIES,
    CONDITION_ERROR,
    EXTRACT_VALUE_ERROR,
    CALLED_ELEMENT_ERROR,
    UNHANDLED_ERROR_EVENT,
    MESSAGE_SIZE_EXCEEDED,
    CALLED_DECISION_ERROR,
    DECISION_EVALUATION_ERROR,
    FORM_NOT_FOUND,
    RESOURCE_NOT_FOUND,
    UNKNOWN_ENUM_VALUE;
  }
}

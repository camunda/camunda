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

/**
 * Represents error types that are used to raise an incident in Zeebe.
 *
 * <p>When introducing a new {@code ErrorType}, ensure that it is properly recognized across related
 * components. To integrate a new error type, check and update the following places:
 *
 * <table>
 *   <thead>
 *     <tr>
 *       <th>Module</th>
 *       <th>Required updates</th>
 *     </tr>
 *   </thead>
 *   <tbody>
 *     <tr>
 *       <td><b>Webapps Schema</b></td>
 *       <td>Add the new type in <code>entities/operate/ErrorType.java</code></td>
 *     </tr>
 *     <tr>
 *       <td><b>Gateway Protocol</b></td>
 *       <td>Add the new type in <code>src/main/proto/rest-api.yaml</code> for the following documents:
 *           <ul><code>IncidentFilterRequestBase</code></ul>
 *           <ul><code>IncidentItemBase</code></ul>
 *       </td>
 *     </tr>
 *   </tbody>
 * </table>
 *
 * <p>Failure to update all necessary components may result in missing or unrecognized incidents
 * when querying or displaying incidents in Operate.
 */
public enum ErrorType {
  UNKNOWN,

  IO_MAPPING_ERROR,

  JOB_NO_RETRIES,

  EXECUTION_LISTENER_NO_RETRIES,

  CONDITION_ERROR,

  EXTRACT_VALUE_ERROR,

  CALLED_ELEMENT_ERROR,

  UNHANDLED_ERROR_EVENT,

  MESSAGE_SIZE_EXCEEDED,

  CALLED_DECISION_ERROR,

  DECISION_EVALUATION_ERROR,

  FORM_NOT_FOUND,

  RESOURCE_NOT_FOUND
}

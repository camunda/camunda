/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.operate.entities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.zeebe.protocol.BpmnElementType;

public enum ActivityType {

  UNSPECIFIED,
  PROCESS,
  SUB_PROCESS,
  START_EVENT,
  INTERMEDIATE_CATCH_EVENT,
  BOUNDARY_EVENT,
  END_EVENT,
  SERVICE_TASK,
  RECEIVE_TASK,
  EXCLUSIVE_GATEWAY,
  PARALLEL_GATEWAY,
  EVENT_BASED_GATEWAY,
  SEQUENCE_FLOW,
  UNKNOWN;

  private static final Logger logger = LoggerFactory.getLogger(EventType.class);

  public static ActivityType fromZeebeBpmnElementType(BpmnElementType type) {
    if (type == null) {
      return UNSPECIFIED;
    }
    try {
      return ActivityType.valueOf(type.toString());
    } catch (IllegalArgumentException ex) {
      logger.error("Activity type not found for value [{}]. UNKNOWN type will be assigned.", type);
      return UNKNOWN;
    }
  }

}

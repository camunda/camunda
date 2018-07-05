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

public enum EventType {

  CREATED,
  COMPLETED,
  CANCELED,

  PAYLOAD_UPDATED,

  START_EVENT_OCCURRED,
  END_EVENT_OCCURRED,

  SEQUENCE_FLOW_TAKEN,

  GATEWAY_ACTIVATED,

  ACTIVITY_READY,
  ACTIVITY_ACTIVATED,
  ACTIVITY_COMPLETING,
  ACTIVITY_COMPLETED,
  ACTIVITY_TERMINATED,

  RESOLVED,
  RESOLVE_FAILED,
  DELETED,

  ACTIVATED,
  TIMED_OUT,
  FAILED,
  RETRIES_UPDATED,

  UNKNOWN;

  private static Logger logger = LoggerFactory.getLogger(EventType.class);

  public static EventType fromZeebeIntent(String intent) {
    try {
      return EventType.valueOf(intent);
    } catch (IllegalArgumentException ex) {
      logger.error("Event type not found for value [{}]. UNKNOWN type will be assigned.", intent);
      return UNKNOWN;
    }
  }

}

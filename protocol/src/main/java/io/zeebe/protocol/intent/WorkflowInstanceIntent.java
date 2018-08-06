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
package io.zeebe.protocol.intent;

public enum WorkflowInstanceIntent implements Intent {
  CREATE((short) 0),
  CREATED((short) 1),

  START_EVENT_OCCURRED((short) 2),
  END_EVENT_OCCURRED((short) 3),
  SEQUENCE_FLOW_TAKEN((short) 4),
  GATEWAY_ACTIVATED((short) 5),

  ACTIVITY_READY((short) 6),
  ACTIVITY_ACTIVATED((short) 7),
  ACTIVITY_COMPLETING((short) 8),
  ACTIVITY_COMPLETED((short) 9),
  ACTIVITY_TERMINATING((short) 10),
  ACTIVITY_TERMINATED((short) 11),

  COMPLETED((short) 12),

  CANCEL((short) 13),
  CANCELING((short) 14),
  CANCELED((short) 15),

  UPDATE_PAYLOAD((short) 16),
  PAYLOAD_UPDATED((short) 17),

  CATCH_EVENT_ENTERING((short) 18),
  CATCH_EVENT_ENTERED((short) 19),
  CATCH_EVENT_OCCURRING((short) 20),
  CATCH_EVENT_OCCURRED((short) 21);

  private short value;

  WorkflowInstanceIntent(short value) {
    this.value = value;
  }

  public short getIntent() {
    return value;
  }

  public static Intent from(short value) {
    switch (value) {
      case 0:
        return CREATE;
      case 1:
        return CREATED;
      case 2:
        return START_EVENT_OCCURRED;
      case 3:
        return END_EVENT_OCCURRED;
      case 4:
        return SEQUENCE_FLOW_TAKEN;
      case 5:
        return GATEWAY_ACTIVATED;
      case 6:
        return ACTIVITY_READY;
      case 7:
        return ACTIVITY_ACTIVATED;
      case 8:
        return ACTIVITY_COMPLETING;
      case 9:
        return ACTIVITY_COMPLETED;
      case 10:
        return ACTIVITY_TERMINATING;
      case 11:
        return ACTIVITY_TERMINATED;
      case 12:
        return COMPLETED;
      case 13:
        return CANCEL;
      case 14:
        return CANCELING;
      case 15:
        return CANCELED;
      case 16:
        return UPDATE_PAYLOAD;
      case 17:
        return PAYLOAD_UPDATED;
      case 18:
        return CATCH_EVENT_ENTERING;
      case 19:
        return CATCH_EVENT_ENTERED;
      case 20:
        return CATCH_EVENT_OCCURRING;
      case 21:
        return CATCH_EVENT_OCCURRED;
      default:
        return Intent.UNKNOWN;
    }
  }

  @Override
  public short value() {
    return value;
  }
}

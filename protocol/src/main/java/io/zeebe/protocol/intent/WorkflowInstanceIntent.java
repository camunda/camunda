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
  START_EVENT_OCCURRED((short) 1),
  END_EVENT_OCCURRED((short) 2),
  SEQUENCE_FLOW_TAKEN((short) 3),
  GATEWAY_ACTIVATED((short) 4),

  ELEMENT_READY((short) 5),
  ELEMENT_ACTIVATED((short) 6),
  ELEMENT_COMPLETING((short) 7),
  ELEMENT_COMPLETED((short) 8),
  ELEMENT_TERMINATING((short) 9),
  ELEMENT_TERMINATED((short) 10),

  CANCEL((short) 11),

  UPDATE_PAYLOAD((short) 12),
  PAYLOAD_UPDATED((short) 13),

  BOUNDARY_EVENT_TRIGGERED((short) 14);

  private final short value;

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
        return START_EVENT_OCCURRED;
      case 2:
        return END_EVENT_OCCURRED;
      case 3:
        return SEQUENCE_FLOW_TAKEN;
      case 4:
        return GATEWAY_ACTIVATED;
      case 5:
        return ELEMENT_READY;
      case 6:
        return ELEMENT_ACTIVATED;
      case 7:
        return ELEMENT_COMPLETING;
      case 8:
        return ELEMENT_COMPLETED;
      case 9:
        return ELEMENT_TERMINATING;
      case 10:
        return ELEMENT_TERMINATED;
      case 11:
        return CANCEL;
      case 12:
        return UPDATE_PAYLOAD;
      case 13:
        return PAYLOAD_UPDATED;
      case 14:
        return BOUNDARY_EVENT_TRIGGERED;
      default:
        return Intent.UNKNOWN;
    }
  }

  @Override
  public short value() {
    return value;
  }
}

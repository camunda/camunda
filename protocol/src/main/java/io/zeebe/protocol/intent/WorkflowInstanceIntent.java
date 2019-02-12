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
  CANCEL((short) 1),

  UPDATE_PAYLOAD((short) 2),
  PAYLOAD_UPDATED((short) 3),

  SEQUENCE_FLOW_TAKEN((short) 4),

  ELEMENT_ACTIVATING((short) 5),
  ELEMENT_ACTIVATED((short) 6),
  ELEMENT_COMPLETING((short) 7),
  ELEMENT_COMPLETED((short) 8),
  ELEMENT_TERMINATING((short) 9),
  ELEMENT_TERMINATED((short) 10),

  EVENT_OCCURRED((short) 11);

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
        return CANCEL;
      case 2:
        return UPDATE_PAYLOAD;
      case 3:
        return PAYLOAD_UPDATED;
      case 4:
        return SEQUENCE_FLOW_TAKEN;
      case 5:
        return ELEMENT_ACTIVATING;
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
        return EVENT_OCCURRED;
      default:
        return Intent.UNKNOWN;
    }
  }

  @Override
  public short value() {
    return value;
  }
}

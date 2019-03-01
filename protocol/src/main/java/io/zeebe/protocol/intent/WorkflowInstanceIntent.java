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

public enum WorkflowInstanceIntent implements WorkflowInstanceRelatedIntent {
  CREATE((short) 0, false),
  CANCEL((short) 1, false),

  SEQUENCE_FLOW_TAKEN((short) 2),

  ELEMENT_ACTIVATING((short) 3),
  ELEMENT_ACTIVATED((short) 4),
  ELEMENT_COMPLETING((short) 5),
  ELEMENT_COMPLETED((short) 6),
  ELEMENT_TERMINATING((short) 7),
  ELEMENT_TERMINATED((short) 8),

  EVENT_OCCURRED((short) 9);

  private final short value;
  private final boolean shouldBlacklist;

  WorkflowInstanceIntent(short value) {
    this(value, true);
  }

  WorkflowInstanceIntent(short value, boolean shouldBlacklist) {
    this.value = value;
    this.shouldBlacklist = shouldBlacklist;
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
        return SEQUENCE_FLOW_TAKEN;
      case 3:
        return ELEMENT_ACTIVATING;
      case 4:
        return ELEMENT_ACTIVATED;
      case 5:
        return ELEMENT_COMPLETING;
      case 6:
        return ELEMENT_COMPLETED;
      case 7:
        return ELEMENT_TERMINATING;
      case 8:
        return ELEMENT_TERMINATED;
      case 9:
        return EVENT_OCCURRED;
      default:
        return Intent.UNKNOWN;
    }
  }

  @Override
  public short value() {
    return value;
  }

  @Override
  public boolean shouldBlacklistInstanceOnError() {
    return shouldBlacklist;
  }
}

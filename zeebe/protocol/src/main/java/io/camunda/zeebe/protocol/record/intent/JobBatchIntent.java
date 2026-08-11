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
package io.camunda.zeebe.protocol.record.intent;

public enum JobBatchIntent implements Intent {
  ACTIVATE((short) 0),
  ACTIVATED((short) 1),

  /**
   * Gateway confirms it received the activation response for the delivery attempt key. Clears the
   * pending delivery without changing job state.
   */
  ACKNOWLEDGE((short) 2),
  ACKNOWLEDGED((short) 3),

  /**
   * Gateway or the delivery-ack timer rejects a pending delivery. Yields jobs that are still
   * activated for that attempt so they become activatable again.
   */
  REJECT((short) 4),
  REJECTED((short) 5);

  private final short value;

  JobBatchIntent(final short value) {
    this.value = value;
  }

  public short getIntent() {
    return value;
  }

  public static Intent from(final short value) {
    switch (value) {
      case 0:
        return ACTIVATE;
      case 1:
        return ACTIVATED;
      case 2:
        return ACKNOWLEDGE;
      case 3:
        return ACKNOWLEDGED;
      case 4:
        return REJECT;
      case 5:
        return REJECTED;
      default:
        return Intent.UNKNOWN;
    }
  }

  @Override
  public short value() {
    return value;
  }

  @Override
  public boolean isEvent() {
    switch (this) {
      case ACTIVATED:
      case ACKNOWLEDGED:
      case REJECTED:
        return true;
      default:
        return false;
    }
  }
}

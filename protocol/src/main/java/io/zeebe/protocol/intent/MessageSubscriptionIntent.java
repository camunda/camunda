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

public enum MessageSubscriptionIntent implements WorkflowInstanceRelatedIntent {
  OPEN((short) 0),
  OPENED((short) 1),

  CORRELATE((short) 2),
  CORRELATED((short) 3),

  REJECT((short) 4),
  REJECTED((short) 5),

  CLOSE((short) 6),
  CLOSED((short) 7);

  private final short value;
  private final boolean shouldBlacklist;

  MessageSubscriptionIntent(short value) {
    this(value, true);
  }

  MessageSubscriptionIntent(short value, boolean shouldBlacklist) {
    this.value = value;
    this.shouldBlacklist = shouldBlacklist;
  }

  @Override
  public short value() {
    return value;
  }

  public static Intent from(short value) {
    switch (value) {
      case 0:
        return OPEN;
      case 1:
        return OPENED;
      case 2:
        return CORRELATE;
      case 3:
        return CORRELATED;
      case 4:
        return REJECT;
      case 5:
        return REJECTED;
      case 6:
        return CLOSE;
      case 7:
        return CLOSED;
      default:
        return Intent.UNKNOWN;
    }
  }

  @Override
  public boolean shouldBlacklistInstanceOnError() {
    return shouldBlacklist;
  }
}

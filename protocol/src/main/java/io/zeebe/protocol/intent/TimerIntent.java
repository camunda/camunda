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

public enum TimerIntent implements WorkflowInstanceRelatedIntent {
  CREATE((short) 0),
  CREATED((short) 1),

  TRIGGER((short) 2),
  TRIGGERED((short) 3),

  CANCEL((short) 4),
  CANCELED((short) 5);

  private final short value;
  private final boolean shouldBlacklist;

  TimerIntent(short value) {
    this(value, true);
  }

  TimerIntent(short value, boolean shouldBlacklist) {
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
        return CREATE;
      case 1:
        return CREATED;
      case 2:
        return TRIGGER;
      case 3:
        return TRIGGERED;
      case 4:
        return CANCEL;
      case 5:
        return CANCELED;
      default:
        return Intent.UNKNOWN;
    }
  }

  @Override
  public boolean shouldBlacklistInstanceOnError() {
    return shouldBlacklist;
  }
}

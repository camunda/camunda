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

public enum AgentHistoryIntent implements Intent {
  CREATE((short) 0, false),
  CREATED((short) 1, true),
  COMMIT((short) 2, false),
  COMMITTED((short) 3, true),
  DISCARDED((short) 4, true);

  private final short value;
  private final boolean isEvent;

  AgentHistoryIntent(final short value, final boolean isEvent) {
    this.value = value;
    this.isEvent = isEvent;
  }

  @Override
  public short value() {
    return value;
  }

  @Override
  public boolean isEvent() {
    return isEvent;
  }

  public static Intent from(final short value) {
    switch (value) {
      case 0:
        return CREATE;
      case 1:
        return CREATED;
      case 2:
        return COMMIT;
      case 3:
        return COMMITTED;
      case 4:
        return DISCARDED;
      default:
        return Intent.UNKNOWN;
    }
  }
}

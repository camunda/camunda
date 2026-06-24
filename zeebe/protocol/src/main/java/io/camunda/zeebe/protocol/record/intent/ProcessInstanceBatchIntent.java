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

public enum ProcessInstanceBatchIntent implements ProcessInstanceRelatedIntent {
  TERMINATE(0),
  ACTIVATE(1),
  TERMINATED(2),
  ACTIVATED(3);

  private final short value;
  private final boolean shouldBanInstance;

  ProcessInstanceBatchIntent(final int value) {
    this(value, true);
  }

  ProcessInstanceBatchIntent(final int value, final boolean shouldBanInstance) {
    this.value = (short) value;
    this.shouldBanInstance = shouldBanInstance;
  }

  public static Intent from(final short value) {
    switch (value) {
      case 0:
        return TERMINATE;
      case 1:
        return ACTIVATE;
      case 2:
        return TERMINATED;
      case 3:
        return ACTIVATED;
      default:
        return UNKNOWN;
    }
  }

  @Override
  public short value() {
    return value;
  }

  @Override
  public boolean isEvent() {
    switch (this) {
      case TERMINATED:
      case ACTIVATED:
        return true;
      default:
        return false;
    }
  }

  @Override
  public boolean shouldBanInstanceOnError() {
    return shouldBanInstance;
  }
}

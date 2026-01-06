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

public enum ConditionalSubscriptionIntent implements ProcessInstanceRelatedIntent {
  CREATED((short) 0),
  TRIGGER((short) 1),
  TRIGGERED((short) 2),
  DELETED((short) 3);

  private final short value;
  private final boolean shouldBanInstance;

  ConditionalSubscriptionIntent(final short value) {
    this(value, true);
  }

  ConditionalSubscriptionIntent(final short value, final boolean shouldBanInstance) {
    this.value = value;
    this.shouldBanInstance = shouldBanInstance;
  }

  @Override
  public short value() {
    return value;
  }

  @Override
  public boolean isEvent() {
    switch (this) {
      case CREATED:
      case TRIGGERED:
      case DELETED:
        return true;
      default:
        return false;
    }
  }

  public static Intent from(final short value) {
    switch (value) {
      case 0:
        return CREATED;
      case 1:
        return TRIGGER;
      case 2:
        return TRIGGERED;
      case 3:
        return DELETED;
      default:
        return Intent.UNKNOWN;
    }
  }

  @Override
  public boolean shouldBanInstanceOnError() {
    return shouldBanInstance;
  }
}

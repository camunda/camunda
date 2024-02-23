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

public enum ProcessMessageSubscriptionIntent implements ProcessInstanceRelatedIntent {
  CREATING((short) 0),
  CREATE((short) 1),
  CREATED((short) 2),

  CORRELATE((short) 3),
  CORRELATED((short) 4),

  DELETING((short) 5),
  DELETE((short) 6),
  DELETED((short) 7),

  /**
   * Event that indicates that the process message subscription is currently migrating. This can be
   * used to prevent the subscription from being correlated while the process instance is being
   * migrated.
   */
  MIGRATING((short) 8),

  /**
   * Command send by the message subscription partition to indicate that the message subscription
   * has been migrated and the process message subscription can be considered fully migrated.
   */
  MIGRATE((short) 9),

  /**
   * Event that indicates that the process message subscription has been migrated completely, i.e.
   * the message subscription has also been migrated. This can be used to allow the subscription to
   * be correlated again.
   */
  MIGRATED((short) 10);

  private final short value;
  private final boolean shouldBanInstance;

  ProcessMessageSubscriptionIntent(final short value) {
    this(value, true);
  }

  ProcessMessageSubscriptionIntent(final short value, final boolean shouldBanInstance) {
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
      case CREATING:
      case CREATED:
      case CORRELATED:
      case DELETING:
      case DELETED:
      case MIGRATING:
      case MIGRATED:
        return true;
      default:
        return false;
    }
  }

  public static Intent from(final short value) {
    switch (value) {
      case 0:
        return CREATING;
      case 1:
        return CREATE;
      case 2:
        return CREATED;
      case 3:
        return CORRELATE;
      case 4:
        return CORRELATED;
      case 5:
        return DELETING;
      case 6:
        return DELETE;
      case 7:
        return DELETED;
      case 8:
        return MIGRATING;
      case 9:
        return MIGRATE;
      case 10:
        return MIGRATED;
      default:
        return Intent.UNKNOWN;
    }
  }

  @Override
  public boolean shouldBanInstanceOnError() {
    return shouldBanInstance;
  }
}

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

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public enum UserTaskIntent implements ProcessInstanceRelatedIntent {
  CREATING(0),
  CREATED(1),

  COMPLETE(2, false),
  COMPLETING(3),
  COMPLETED(4),

  CANCELING(5),
  CANCELED(6),

  ASSIGN(7),
  ASSIGNING(8),
  ASSIGNED(9),

  CLAIM(10),

  UPDATE(11),
  UPDATING(12),
  UPDATED(13),

  MIGRATED(14);

  private final short value;
  private final boolean shouldBanInstance;

  UserTaskIntent(final int value) {
    this(value, true);
  }

  UserTaskIntent(final int value, final boolean shouldBanInstance) {
    this.value = (short) value;
    this.shouldBanInstance = shouldBanInstance;
  }

  public short getIntent() {
    return value;
  }

  public static Intent from(final short value) {
    switch (value) {
      case 0:
        return CREATING;
      case 1:
        return CREATED;
      case 2:
        return COMPLETE;
      case 3:
        return COMPLETING;
      case 4:
        return COMPLETED;
      case 5:
        return CANCELING;
      case 6:
        return CANCELED;
      case 7:
        return ASSIGN;
      case 8:
        return ASSIGNING;
      case 9:
        return ASSIGNED;
      case 10:
        return CLAIM;
      case 11:
        return UPDATE;
      case 12:
        return UPDATING;
      case 13:
        return UPDATED;
      case 14:
        return MIGRATED;
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
      case CREATING:
      case CREATED:
      case COMPLETING:
      case COMPLETED:
      case CANCELING:
      case CANCELED:
      case ASSIGNING:
      case ASSIGNED:
      case UPDATING:
      case UPDATED:
      case MIGRATED:
        return true;
      default:
        return false;
    }
  }

  @Override
  public boolean shouldBanInstanceOnError() {
    return shouldBanInstance;
  }

  public static Set<UserTaskIntent> commands() {
    return Stream.of(UserTaskIntent.values())
        .filter(intent -> !intent.isEvent())
        .collect(Collectors.toSet());
  }
}

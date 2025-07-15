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
package io.camunda.zeebe.protocol.record.intent.management;

import io.camunda.zeebe.protocol.record.intent.Intent;

public enum CheckpointIntent implements Intent {
  CREATE(0),
  CREATED(1),
  IGNORED(2),
  CONFIRM_BACKUP(3),
  CONFIRMED_BACKUP(4);

  private final short value;

  CheckpointIntent(final int value) {
    this.value = (short) value;
  }

  @Override
  public short value() {
    return value;
  }

  @Override
  public boolean isEvent() {
    switch (this) {
      case CREATED:
      case IGNORED:
      case CONFIRMED_BACKUP:
        return true;
      default:
        return false;
    }
  }

  public static Intent from(final short value) {
    switch (value) {
      case 0:
        return CREATE;
      case 1:
        return CREATED;
      case 2:
        return IGNORED;
      case 3:
        return CONFIRM_BACKUP;
      case 4:
        return CONFIRMED_BACKUP;
      default:
        return UNKNOWN;
    }
  }
}

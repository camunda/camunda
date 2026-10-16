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

public enum LogArchiveIntent implements Intent {
  START_TIMELINE(0),
  TIMELINE_STARTED(1),
  ARCHIVE_LOG(2),
  LOG_ARCHIVED(3),
  ARCHIVE_SNAPSHOT(4),
  SNAPSHOT_ARCHIVED(5),
  SEAL_INDEX(6),
  INDEX_SEALED(7),
  CONFIRM_PUBLISHED(8),
  PUBLISHED(9);

  private final short value;

  LogArchiveIntent(final int value) {
    this.value = (short) value;
  }

  @Override
  public short value() {
    return value;
  }

  @Override
  public boolean isEvent() {
    switch (this) {
      case TIMELINE_STARTED:
      case LOG_ARCHIVED:
      case SNAPSHOT_ARCHIVED:
      case INDEX_SEALED:
      case PUBLISHED:
        return true;
      default:
        return false;
    }
  }

  public static Intent from(final short value) {
    switch (value) {
      case 0:
        return START_TIMELINE;
      case 1:
        return TIMELINE_STARTED;
      case 2:
        return ARCHIVE_LOG;
      case 3:
        return LOG_ARCHIVED;
      case 4:
        return ARCHIVE_SNAPSHOT;
      case 5:
        return SNAPSHOT_ARCHIVED;
      case 6:
        return SEAL_INDEX;
      case 7:
        return INDEX_SEALED;
      case 8:
        return CONFIRM_PUBLISHED;
      case 9:
        return PUBLISHED;
      default:
        return UNKNOWN;
    }
  }
}

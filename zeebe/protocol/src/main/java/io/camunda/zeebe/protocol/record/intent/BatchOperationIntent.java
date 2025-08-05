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

public enum BatchOperationIntent implements Intent {
  CREATE((short) 0),
  CREATED((short) 1),
  INITIALIZE((short) 2),
  INITIALIZING((short) 3),
  FINISH_INITIALIZATION((short) 4),
  INITIALIZED((short) 5),
  FAIL((short) 6),
  FAILED((short) 7),
  CANCEL((short) 8),
  CANCELED((short) 9),
  SUSPEND((short) 10),
  SUSPENDED((short) 11),
  RESUME((short) 12),
  RESUMED((short) 13),
  COMPLETE((short) 14),
  COMPLETED((short) 15),
  COMPLETE_PARTITION((short) 16),
  PARTITION_COMPLETED((short) 17),
  FAIL_PARTITION((short) 18),
  PARTITION_FAILED((short) 19);

  private final short value;

  BatchOperationIntent(final short value) {
    this.value = value;
  }

  public short getIntent() {
    return value;
  }

  public static Intent from(final short value) {
    switch (value) {
      case 0:
        return CREATE;
      case 1:
        return CREATED;
      case 2:
        return INITIALIZE;
      case 3:
        return INITIALIZING;
      case 4:
        return FINISH_INITIALIZATION;
      case 5:
        return INITIALIZED;
      case 6:
        return FAIL;
      case 7:
        return FAILED;
      case 8:
        return CANCEL;
      case 9:
        return CANCELED;
      case 10:
        return SUSPEND;
      case 11:
        return SUSPENDED;
      case 12:
        return RESUME;
      case 13:
        return RESUMED;
      case 14:
        return COMPLETE;
      case 15:
        return COMPLETED;
      case 16:
        return COMPLETE_PARTITION;
      case 17:
        return PARTITION_COMPLETED;
      case 18:
        return FAIL_PARTITION;
      case 19:
        return PARTITION_FAILED;
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
      case CREATED:
      case INITIALIZED:
      case FAILED:
      case SUSPENDED:
      case CANCELED:
      case RESUMED:
      case COMPLETED:
      case PARTITION_COMPLETED:
      case PARTITION_FAILED:
      case INITIALIZING:
        return true;
      default:
        return false;
    }
  }
}

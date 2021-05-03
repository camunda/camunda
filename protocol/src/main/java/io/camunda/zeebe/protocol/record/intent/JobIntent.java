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
package io.zeebe.protocol.record.intent;

public enum JobIntent implements ProcessInstanceRelatedIntent {
  CREATED((short) 0),

  COMPLETE((short) 1, false),
  COMPLETED((short) 2),

  TIME_OUT((short) 3),
  TIMED_OUT((short) 4),

  FAIL((short) 5, false),
  FAILED((short) 6),

  UPDATE_RETRIES((short) 7, false),
  RETRIES_UPDATED((short) 8),

  CANCEL((short) 9),
  CANCELED((short) 10),

  THROW_ERROR((short) 11, false),
  ERROR_THROWN((short) 12);

  private final short value;
  private final boolean shouldBlacklist;

  JobIntent(final short value) {
    this(value, true);
  }

  JobIntent(final short value, final boolean shouldBlacklist) {
    this.value = value;
    this.shouldBlacklist = shouldBlacklist;
  }

  public short getIntent() {
    return value;
  }

  public static Intent from(final short value) {
    switch (value) {
      case 0:
        return CREATED;
      case 1:
        return COMPLETE;
      case 2:
        return COMPLETED;
      case 3:
        return TIME_OUT;
      case 4:
        return TIMED_OUT;
      case 5:
        return FAIL;
      case 6:
        return FAILED;
      case 7:
        return UPDATE_RETRIES;
      case 8:
        return RETRIES_UPDATED;
      case 9:
        return CANCEL;
      case 10:
        return CANCELED;
      case 11:
        return THROW_ERROR;
      case 12:
        return ERROR_THROWN;
      default:
        return UNKNOWN;
    }
  }

  @Override
  public short value() {
    return value;
  }

  @Override
  public boolean shouldBlacklistInstanceOnError() {
    return shouldBlacklist;
  }
}

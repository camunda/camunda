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
  /**
   * @deprecated to be removed after engine refactoring TODO (#6202) remove at the end of engine
   *     refactoring
   */
  @Deprecated
  CREATE((short) 0),
  CREATED((short) 1),

  COMPLETE((short) 2, false),
  COMPLETED((short) 3),

  TIME_OUT((short) 4),
  TIMED_OUT((short) 5),

  FAIL((short) 6, false),
  FAILED((short) 7),

  UPDATE_RETRIES((short) 8, false),
  RETRIES_UPDATED((short) 9),

  CANCEL((short) 10),
  CANCELED((short) 11),

  THROW_ERROR((short) 12, false),
  ERROR_THROWN((short) 13);

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
        return CREATE;
      case 1:
        return CREATED;
      case 2:
        return COMPLETE;
      case 3:
        return COMPLETED;
      case 4:
        return TIME_OUT;
      case 5:
        return TIMED_OUT;
      case 6:
        return FAIL;
      case 7:
        return FAILED;
      case 8:
        return UPDATE_RETRIES;
      case 9:
        return RETRIES_UPDATED;
      case 10:
        return CANCEL;
      case 11:
        return CANCELED;
      case 12:
        return THROW_ERROR;
      case 13:
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

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

public enum JobIntent implements WorkflowInstanceRelatedIntent {
  /**
   * @deprecated to be removed after engine refactoring TODO (#6202) remove at the end of engine
   *     refactoring
   */
  @Deprecated
  CREATE((short) 0),
  CREATED((short) 1),

  ACTIVATED((short) 2),

  COMPLETE((short) 3, false),
  COMPLETED((short) 4),

  TIME_OUT((short) 5),
  TIMED_OUT((short) 6),

  FAIL((short) 7, false),
  FAILED((short) 8),

  UPDATE_RETRIES((short) 9, false),
  RETRIES_UPDATED((short) 10),

  CANCEL((short) 11),
  CANCELED((short) 12),

  THROW_ERROR((short) 13, false),
  ERROR_THROWN((short) 14);

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
        return ACTIVATED;
      case 3:
        return COMPLETE;
      case 4:
        return COMPLETED;
      case 5:
        return TIME_OUT;
      case 6:
        return TIMED_OUT;
      case 7:
        return FAIL;
      case 8:
        return FAILED;
      case 9:
        return UPDATE_RETRIES;
      case 10:
        return RETRIES_UPDATED;
      case 11:
        return CANCEL;
      case 12:
        return CANCELED;
      case 13:
        return THROW_ERROR;
      case 14:
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

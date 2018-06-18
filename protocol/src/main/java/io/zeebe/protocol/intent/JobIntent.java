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
package io.zeebe.protocol.intent;

public enum JobIntent implements Intent {
  CREATE((short) 0),
  CREATED((short) 1),

  ACTIVATE((short) 2),
  ACTIVATED((short) 3),

  COMPLETE((short) 4),
  COMPLETED((short) 5),

  TIME_OUT((short) 6),
  TIMED_OUT((short) 7),

  FAIL((short) 8),
  FAILED((short) 9),

  UPDATE_RETRIES((short) 10),
  RETRIES_UPDATED((short) 11),

  CANCEL((short) 12),
  CANCELED((short) 13);

  private short value;

  JobIntent(short value) {
    this.value = value;
  }

  public short getIntent() {
    return value;
  }

  public static Intent from(short value) {
    switch (value) {
      case 0:
        return CREATE;
      case 1:
        return CREATED;
      case 2:
        return ACTIVATE;
      case 3:
        return ACTIVATED;
      case 4:
        return COMPLETE;
      case 5:
        return COMPLETED;
      case 6:
        return TIME_OUT;
      case 7:
        return TIMED_OUT;
      case 8:
        return FAIL;
      case 9:
        return FAILED;
      case 10:
        return UPDATE_RETRIES;
      case 11:
        return RETRIES_UPDATED;
      case 12:
        return CANCEL;
      case 13:
        return CANCELED;
      default:
        return Intent.UNKNOWN;
    }
  }

  @Override
  public short value() {
    return value;
  }
}

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
  START((short) 2),
  STARTED((short) 3),
  FAIL((short) 4),
  FAILED((short) 5),
  CANCEL((short) 6),
  CANCELED((short) 7),
  PAUSE((short) 8),
  // PAUSED((short) 9),
  RESUME((short) 10);
  // RESUMED((short) 11);

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
        return START;
      case 3:
        return STARTED;
      case 4:
        return FAIL;
      case 5:
        return FAILED;
      case 6:
        return CANCEL;
      case 7:
        return CANCELED;
      case 8:
        return PAUSE;
      case 10:
        return RESUME;

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
      case STARTED:
      case FAILED:
      case CANCELED:
        return true;
      default:
        return false;
    }
  }
}

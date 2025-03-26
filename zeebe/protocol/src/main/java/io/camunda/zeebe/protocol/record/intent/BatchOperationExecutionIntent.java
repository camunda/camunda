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

public enum BatchOperationExecutionIntent implements Intent {
  EXECUTE((short) 0),
  EXECUTING((short) 1),
  EXECUTED((short) 2),

  COMPLETED((short) 3),

  CANCEL((short) 4),
  //  CANCELED((short) 5),

  PAUSE((short) 6),
  //  PAUSED((short) 7),

  RESUME((short) 8);
  //  RESUMED((short) 9),

  private final short value;

  BatchOperationExecutionIntent(final short value) {
    this.value = value;
  }

  public short getIntent() {
    return value;
  }

  public static Intent from(final short value) {
    switch (value) {
      case 0:
        return EXECUTE;
      case 4:
        return CANCEL;
      case 6:
        return PAUSE;
      case 8:
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
      case EXECUTING:
      case EXECUTED:
      case COMPLETED:
        return true;
      default:
        return false;
    }
  }
}

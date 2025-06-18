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

public enum AdHocSubProcessInstructionIntent implements Intent {
  ACTIVATE(0),
  ACTIVATED(1);

  private final short value;

  AdHocSubProcessInstructionIntent(final int value) {
    this.value = (short) value;
  }

  public short getIntent() {
    return value;
  }

  @Override
  public short value() {
    return value;
  }

  @Override
  public boolean isEvent() {
    switch (this) {
      case ACTIVATED:
        return true;
      default:
        return false;
    }
  }

  public static Intent from(final short value) {
    switch (value) {
      case 0:
        return ACTIVATE;
      case 1:
        return ACTIVATED;
      default:
        return Intent.UNKNOWN;
    }
  }
}

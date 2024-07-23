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

public enum ScaleIntent implements Intent {
  RELOCATE_MESSAGES_START(0),
  RELOCATE_MESSAGES_STARTED(1),
  RELOCATE_MESSAGES_COMPLETED(2),

  // per correlationKey (TODO: This could be per messageName and correlationKey)
  MSG_SUBSCRIPTION_RELOCATION_START(3),
  MSG_SUBSCRIPTION_RELOCATION_STARTED(4),
  MSG_SUBSCRIPTION_RELOCATION_CONTINUE(5),
  MSG_SUBSCRIPTION_RELOCATION_COMPLETED(6),

  // a single subscription record
  MSG_SUBSCRIPTION_RELOCATION_ACKNOWLEDGE(7),
  MSG_SUBSCRIPTION_RELOCATION_MOVED(8);

  private final short value;

  ScaleIntent(final int value) {
    this.value = (short) value;
  }

  @Override
  public short value() {
    return value;
  }

  @Override
  public boolean isEvent() {
    switch (this) {
      case RELOCATE_MESSAGES_STARTED:
        return true;
      default:
        return false;
    }
  }

  public static Intent from(final short value) {
    switch (value) {
      case 0:
        return RELOCATE_MESSAGES_START;
      case 1:
        return RELOCATE_MESSAGES_STARTED;
      default:
        return Intent.UNKNOWN;
    }
  }
}

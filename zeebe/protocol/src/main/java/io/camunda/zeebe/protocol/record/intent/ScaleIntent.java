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
  RELOCATION_START(0),
  RELOCATION_STARTED(1),
  RELOCATE_NEXT_CORRELATION_KEY(2),
  RELOCATE_CORRELATION_KEY_STARTED(3),
  RELOCATE_MESSAGE_SUBSCRIPTION_START(4),
  RELOCATION_ON_PARTITION_COMPLETED(5),
  RELOCATE_MESSAGE_SUBSCRIPTION_APPLY(6),
  RELOCATE_MESSAGE_SUBSCRIPTION_COMPLETE(7),
  RELOCATE_MESSAGE_SUBSCRIPTION_COMPLETED(8),
  RELOCATE_MESSAGE_START(9),
  RELOCATE_MESSAGE_APPLY(10),
  RELOCATE_MESSAGE_COMPLETE(11),
  RELOCATE_MESSAGE_COMPLETED(12),
  RELOCATE_CORRELATION_KEY_COMPLETED(13),
  RELOCATION_ON_PARTITION_COMPLETE(14),
  RELOCATION_COMPLETED(15);

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
      case RELOCATION_STARTED:
      case RELOCATE_CORRELATION_KEY_STARTED:
      case RELOCATION_ON_PARTITION_COMPLETED:
      case RELOCATE_MESSAGE_SUBSCRIPTION_COMPLETED:
      case RELOCATE_MESSAGE_COMPLETED:
      case RELOCATE_CORRELATION_KEY_COMPLETED:
      case RELOCATION_COMPLETED:
        return true;
      default:
        return false;
    }
  }

  public static Intent from(final short value) {
    switch (value) {
      case 0:
        return RELOCATION_START;
      case 1:
        return RELOCATION_STARTED;
      case 2:
        return RELOCATE_NEXT_CORRELATION_KEY;
      case 3:
        return RELOCATE_CORRELATION_KEY_STARTED;
      case 4:
        return RELOCATE_MESSAGE_SUBSCRIPTION_START;
      case 5:
        return RELOCATION_ON_PARTITION_COMPLETED;
      case 6:
        return RELOCATE_MESSAGE_SUBSCRIPTION_APPLY;
      case 7:
        return RELOCATE_MESSAGE_SUBSCRIPTION_COMPLETE;
      case 8:
        return RELOCATE_MESSAGE_SUBSCRIPTION_COMPLETED;
      case 9:
        return RELOCATE_MESSAGE_START;
      case 10:
        return RELOCATE_MESSAGE_APPLY;
      case 11:
        return RELOCATE_MESSAGE_COMPLETE;
      case 12:
        return RELOCATE_MESSAGE_COMPLETED;
      case 13:
        return RELOCATE_CORRELATION_KEY_COMPLETED;
      case 14:
        return RELOCATION_ON_PARTITION_COMPLETE;
      case 15:
        return RELOCATION_COMPLETED;
      default:
        return Intent.UNKNOWN;
    }
  }
}

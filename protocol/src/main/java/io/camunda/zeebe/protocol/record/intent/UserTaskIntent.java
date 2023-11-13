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

public enum UserTaskIntent implements ProcessInstanceRelatedIntent {
  CREATING(0),
  CREATED(1),

  COMPLETE(2),
  COMPLETING(3),
  COMPLETED(4),

  CANCELING(5),
  CANCELED(6);

  private final short value;

  UserTaskIntent(final int value) {
    this.value = (short) value;
  }

  public short getIntent() {
    return value;
  }

  public static Intent from(final short value) {
    switch (value) {
      case 0:
        return CREATING;
      case 1:
        return CREATED;
      case 2:
        return COMPLETE;
      case 3:
        return COMPLETING;
      case 4:
        return COMPLETED;
      case 5:
        return CANCELING;
      case 6:
        return CANCELED;
      default:
        return UNKNOWN;
    }
  }

  @Override
  public short value() {
    return value;
  }

  @Override
  public boolean shouldBanInstanceOnError() {
    return false;
  }
}

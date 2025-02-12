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

public enum VariableIntent implements Intent {
  CREATING((short) 3),
  CREATED((short) 0),
  UPDATING((short) 4),
  UPDATED((short) 1),
  MIGRATED((short) 2);

  private final short value;

  VariableIntent(final short value) {
    this.value = value;
  }

  @Override
  public short value() {
    return value;
  }

  @Override
  public boolean isEvent() {
    return true;
  }

  public static Intent from(final short value) {
    switch (value) {
      case 0:
        return CREATED;
      case 1:
        return UPDATED;
      case 2:
        return MIGRATED;
      case 3:
        return CREATING;
      case 4:
        return UPDATING;
      default:
        return Intent.UNKNOWN;
    }
  }
}

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
  CREATE((short) 0),
  CREATED((short) 1),
  UPDATE((short) 2),
  UPDATED((short) 3),
  DELETE((short) 4),
  DELETED((short) 5),
  MIGRATED((short) 6);

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
    switch (this) {
      case CREATE:
      case UPDATE:
      case DELETE:
        return false;
      case CREATED:
      case UPDATED:
      case DELETED:
      case MIGRATED:
        return true;
      default:
        throw new IllegalArgumentException(name() + "is neither a command not an event");
    }
  }

  public static Intent from(final short value) {
    switch (value) {
      case 0:
        return CREATE;
      case 1:
        return CREATED;
      case 2:
        return UPDATE;
      case 3:
        return UPDATED;
      case 4:
        return DELETE;
      case 5:
        return DELETED;
      case 6:
        return MIGRATED;
      default:
        return Intent.UNKNOWN;
    }
  }
}

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

public enum AuthorizationIntent implements Intent {
  CREATE(0),
  CREATED(1),
  UPDATE(2),
  UPDATING(3),
  UPDATED(4),
  DELETE(5),
  DELETING(6),
  DELETED(7);

  private final short value;

  AuthorizationIntent(final int value) {
    this.value = (short) value;
  }

  @Override
  public short value() {
    return value;
  }

  @Override
  public boolean isEvent() {
    switch (this) {
      case CREATED:
      case UPDATING:
      case UPDATED:
      case DELETING:
      case DELETED:
        return true;
      default:
        return false;
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
        return UPDATING;
      case 4:
        return UPDATED;
      case 5:
        return DELETE;
      case 6:
        return DELETING;
      case 7:
        return DELETED;
      default:
        return UNKNOWN;
    }
  }
}

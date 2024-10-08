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

public enum TenantIntent implements Intent {
  CREATE(1),
  CREATED(2),
  UPDATE(3),
  UPDATED(4),
  DELETE(5),
  DELETED(6),
  ADD_ENTITY(7),
  ENTITY_ADDED(8),
  REMOVE_ENTITY(9),
  ENTITY_REMOVED(10);

  private final short value;

  TenantIntent(final int value) {
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
      case UPDATED:
      case DELETED:
      case ENTITY_ADDED:
      case ENTITY_REMOVED:
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
        return UPDATED;
      case 4:
        return DELETE;
      case 5:
        return DELETED;
      case 6:
        return ADD_ENTITY;
      case 7:
        return ENTITY_ADDED;
      case 8:
        return REMOVE_ENTITY;
      case 9:
        return ENTITY_REMOVED;
      default:
        return Intent.UNKNOWN;
    }
  }
}

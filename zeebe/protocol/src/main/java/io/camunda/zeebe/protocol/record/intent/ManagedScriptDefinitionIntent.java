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

public enum ManagedScriptDefinitionIntent implements Intent {
  CREATED(0),
  DELETED(1),
  ACTIVATE(2),
  LEASED(3),
  RENEW_LEASE(4),
  LEASE_RENEWED(5),
  UPDATE(6),
  UPDATED(7),
  GET(8),
  GOT(9);

  private final short value;

  ManagedScriptDefinitionIntent(final int value) {
    this.value = (short) value;
  }

  public static Intent from(final short value) {
    switch (value) {
      case 0:
        return CREATED;
      case 1:
        return DELETED;
      case 2:
        return ACTIVATE;
      case 3:
        return LEASED;
      case 4:
        return RENEW_LEASE;
      case 5:
        return LEASE_RENEWED;
      case 6:
        return UPDATE;
      case 7:
        return UPDATED;
      case 8:
        return GET;
      case 9:
        return GOT;
      default:
        return UNKNOWN;
    }
  }

  @Override
  public short value() {
    return value;
  }

  @Override
  public boolean isEvent() {
    return this == CREATED
        || this == DELETED
        || this == LEASED
        || this == LEASE_RENEWED
        || this == UPDATED
        || this == GOT;
  }
}

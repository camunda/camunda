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

public enum GlobalListenerIntent implements Intent {
  CREATE(0, false),
  CREATED(1, true),
  UPDATE(2, false),
  UPDATED(3, true),
  DELETE(4, false),
  DELETED(5, true);

  private final short value;
  private final boolean event;

  GlobalListenerIntent(final int value, final boolean event) {
    this.value = (short) value;
    this.event = event;
  }

  @Override
  public short value() {
    return value;
  }

  @Override
  public boolean isEvent() {
    return event;
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
      default:
        return UNKNOWN;
    }
  }
}

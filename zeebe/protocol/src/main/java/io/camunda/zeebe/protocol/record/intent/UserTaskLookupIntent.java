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

/** Reads an active user task and its variables from the engine state, without changing it. */
public enum UserTaskLookupIntent implements Intent {

  /** Requests the user task and its variables. */
  FETCH(0),

  /** The user task was read; the variables are only part of the command response. */
  FETCHED(1);

  private final short value;

  UserTaskLookupIntent(final int value) {
    this.value = (short) value;
  }

  public static Intent from(final short value) {
    switch (value) {
      case 0:
        return FETCH;
      case 1:
        return FETCHED;
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
    return this == FETCHED;
  }
}

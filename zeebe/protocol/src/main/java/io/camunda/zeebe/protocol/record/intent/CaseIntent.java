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

/**
 * Events of a case. The engine writes them in the same step as the change that causes them; there
 * are no case commands.
 */
public enum CaseIntent implements Intent {

  /** The case was created for a root process instance, with all stages NOT_STARTED. */
  CREATED(0),

  /** One stage of the case changed its status. */
  STAGE_UPDATED(1),

  /** The process instance of the case completed. */
  COMPLETED(2),

  /** The process instance of the case terminated. */
  TERMINATED(3);

  private final short value;

  CaseIntent(final int value) {
    this.value = (short) value;
  }

  public static Intent from(final short value) {
    switch (value) {
      case 0:
        return CREATED;
      case 1:
        return STAGE_UPDATED;
      case 2:
        return COMPLETED;
      case 3:
        return TERMINATED;
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
    return true;
  }
}

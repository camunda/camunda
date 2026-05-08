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

public enum ClusterConfigurationIntent implements Intent {
  STAMP_CHANGE_PLAN((short) 0),
  CHANGE_PLAN_STAMPED((short) 1),
  APPLY_OPERATION((short) 2),
  OPERATION_APPLIED((short) 3),
  COMPLETE_CHANGE((short) 4),
  CHANGE_COMPLETED((short) 5),
  REJECT((short) 6);

  private final short value;

  ClusterConfigurationIntent(final short value) {
    this.value = value;
  }

  @Override
  public short value() {
    return value;
  }

  @Override
  public boolean isEvent() {
    switch (this) {
      case CHANGE_PLAN_STAMPED:
      case OPERATION_APPLIED:
      case CHANGE_COMPLETED:
      case REJECT:
        return true;
      default:
        return false;
    }
  }

  public static Intent from(final short value) {
    switch (value) {
      case 0:
        return STAMP_CHANGE_PLAN;
      case 1:
        return CHANGE_PLAN_STAMPED;
      case 2:
        return APPLY_OPERATION;
      case 3:
        return OPERATION_APPLIED;
      case 4:
        return COMPLETE_CHANGE;
      case 5:
        return CHANGE_COMPLETED;
      case 6:
        return REJECT;
      default:
        return UNKNOWN;
    }
  }
}

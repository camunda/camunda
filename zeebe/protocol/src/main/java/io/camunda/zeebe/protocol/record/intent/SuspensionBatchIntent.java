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

public enum SuspensionBatchIntent implements ProcessInstanceRelatedIntent {
  SUSPEND_ELEMENT_INSTANCE(0),

  /** Completes traversal of a subtree, without completing the BPMN element. */
  COMPLETE_SUSPENDING_ELEMENT_INSTANCE(1),

  /** Acknowledges a processed traversal step without changing BPMN element state. */
  ELEMENT_INSTANCE_SUSPENDED(2);

  private final short value;

  SuspensionBatchIntent(final int value) {
    this.value = (short) value;
  }

  public static Intent from(final short value) {
    switch (value) {
      case 0:
        return SUSPEND_ELEMENT_INSTANCE;
      case 1:
        return COMPLETE_SUSPENDING_ELEMENT_INSTANCE;
      case 2:
        return ELEMENT_INSTANCE_SUSPENDED;
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
    return this == ELEMENT_INSTANCE_SUSPENDED;
  }

  @Override
  public boolean shouldBanInstanceOnError() {
    return true;
  }
}

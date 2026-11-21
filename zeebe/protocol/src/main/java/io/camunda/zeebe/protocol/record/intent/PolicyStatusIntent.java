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
 * Asks partition 1 for its policy status: the committed policy and the policy update that is open.
 * The broker's Hub receiver asks when it starts and while it waits for an update to be committed.
 */
public enum PolicyStatusIntent implements Intent {
  QUERY(0),
  QUERIED(1);

  private final short value;

  PolicyStatusIntent(final int value) {
    this.value = (short) value;
  }

  @Override
  public short value() {
    return value;
  }

  @Override
  public boolean isEvent() {
    return this == QUERIED;
  }

  public static Intent from(final short value) {
    switch (value) {
      case 0:
        return QUERY;
      case 1:
        return QUERIED;
      default:
        return Intent.UNKNOWN;
    }
  }
}

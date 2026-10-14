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

public enum PolicyUpdateIntent implements Intent {
  APPEND_CHUNK(0),
  CHUNK_APPENDED(1),
  APPLY(2),
  APPLIED(3),
  SWITCH_VERSION(4),
  ABORTED(6);

  private final short value;

  PolicyUpdateIntent(final int value) {
    this.value = (short) value;
  }

  @Override
  public short value() {
    return value;
  }

  @Override
  public boolean isEvent() {
    switch (this) {
      case CHUNK_APPENDED:
      case APPLIED:
      case ABORTED:
        return true;
      default:
        return false;
    }
  }

  public static Intent from(final short value) {
    switch (value) {
      case 0:
        return APPEND_CHUNK;
      case 1:
        return CHUNK_APPENDED;
      case 2:
        return APPLY;
      case 3:
        return APPLIED;
      case 4:
        return SWITCH_VERSION;
      case 6:
        return ABORTED;
      default:
        return Intent.UNKNOWN;
    }
  }
}

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
package io.zeebe.protocol.intent;

public enum VariableDocumentIntent implements Intent {
  UPDATE(0),
  UPDATED(1);

  private short value;

  VariableDocumentIntent(int value) {
    this((short) value);
  }

  VariableDocumentIntent(short value) {
    this.value = value;
  }

  @Override
  public short value() {
    return value;
  }

  public static Intent from(short value) {
    switch (value) {
      case 0:
        return UPDATE;
      case 1:
        return UPDATED;
      default:
        return Intent.UNKNOWN;
    }
  }
}

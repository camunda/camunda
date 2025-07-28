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

public enum MultiInstanceIntent implements ProcessInstanceRelatedIntent {
  INPUT_COLLECTION_EVALUATED(0);

  final short value;

  MultiInstanceIntent(final int value) {
    this.value = (short) value;
  }

  public static Intent from(final short value) {
    if (value == 0) {
      return INPUT_COLLECTION_EVALUATED;
    }
    return Intent.UNKNOWN;
  }

  @Override
  public boolean shouldBanInstanceOnError() {
    return true;
  }

  @Override
  public short value() {
    return value;
  }

  @Override
  public boolean isEvent() {
    return this == INPUT_COLLECTION_EVALUATED;
  }
}

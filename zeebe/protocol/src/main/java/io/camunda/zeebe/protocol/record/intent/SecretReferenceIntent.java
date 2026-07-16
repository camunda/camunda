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

public enum SecretReferenceIntent implements Intent {
  RESOLUTION_COMPLETE(0, false),
  RESOLUTION_FAIL(1, false),
  BATCH_REACTIVATE_JOBS(2, false),
  BATCH_CREATE_INCIDENTS(3, false),
  RESOLUTION_REQUESTED(4, true),
  RESOLUTION_COMPLETED(5, true),
  RESOLUTION_FAILED(6, true),
  BATCH_JOBS_REACTIVATED(7, true),
  BATCH_INCIDENTS_CREATED(8, true);

  private final short value;
  private final boolean isEvent;

  SecretReferenceIntent(final int value, final boolean isEvent) {
    this.value = (short) value;
    this.isEvent = isEvent;
  }

  @Override
  public short value() {
    return value;
  }

  @Override
  public boolean isEvent() {
    return isEvent;
  }

  public static Intent from(final short value) {
    switch (value) {
      case 0:
        return RESOLUTION_COMPLETE;
      case 1:
        return RESOLUTION_FAIL;
      case 2:
        return BATCH_REACTIVATE_JOBS;
      case 3:
        return BATCH_CREATE_INCIDENTS;
      case 4:
        return RESOLUTION_REQUESTED;
      case 5:
        return RESOLUTION_COMPLETED;
      case 6:
        return RESOLUTION_FAILED;
      case 7:
        return BATCH_JOBS_REACTIVATED;
      case 8:
        return BATCH_INCIDENTS_CREATED;
      default:
        return Intent.UNKNOWN;
    }
  }
}

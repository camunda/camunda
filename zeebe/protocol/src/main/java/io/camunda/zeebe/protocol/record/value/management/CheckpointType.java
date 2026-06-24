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
package io.camunda.zeebe.protocol.record.value.management;

public enum CheckpointType {
  MARKER((short) 0),
  SCHEDULED_BACKUP((short) 1),
  MANUAL_BACKUP((short) 2);

  private final short value;

  CheckpointType(final short value) {
    this.value = value;
  }

  public short getValue() {
    return value;
  }

  public static CheckpointType valueOf(final short value) {
    switch (value) {
      case 0:
        {
          return MARKER;
        }
      case 1:
        {
          return SCHEDULED_BACKUP;
        }
      case 2:
        {
          return MANUAL_BACKUP;
        }
      default:
        {
          return null;
        }
    }
  }

  public boolean shouldCreateBackup() {
    return this == SCHEDULED_BACKUP || this == MANUAL_BACKUP;
  }

  public boolean isManual() {
    return this == MANUAL_BACKUP;
  }
}

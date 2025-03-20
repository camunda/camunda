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

import java.util.Arrays;

public enum MappingIntent implements Intent {
  CREATE(0),
  CREATED(1),
  DELETE(2),
  DELETED(3),
  UPDATE(4),
  UPDATED(5);

  private final short value;

  MappingIntent(final int value) {
    this.value = (short) value;
  }

  @Override
  public short value() {
    return value;
  }

  @Override
  public boolean isEvent() {
    switch (this) {
      case CREATED:
      case DELETED:
      case UPDATED:
        return true;
      default:
        return false;
    }
  }

  public static Intent from(final short value) {
    return Arrays.stream(values())
        .filter(m -> m.value() == value)
        .findFirst()
        .map(Intent.class::cast)
        .orElse(Intent.UNKNOWN);
  }
}

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
package io.camunda.zeebe.protocol.record.intent.scaling;

import io.camunda.zeebe.protocol.record.intent.Intent;

public enum RedistributionIntent implements Intent {
  START((short) 1, false),
  STARTED((short) 2, true),
  COMPLETE((short) 5, false),
  COMPLETED((short) 6, true);

  private final short value;
  private final boolean isEvent;

  RedistributionIntent(final short value, final boolean isEvent) {
    this.value = value;
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

  public static Intent from(final short intent) {
    switch (intent) {
      case 1:
        return START;
      case 2:
        return STARTED;
      // old CONTINUE is mapped to COMPLETE
      case 3:
      case 5:
        return COMPLETE;
      case 4:
      // old CONTINUED is mapped to COMPLETED
      case 6:
        return COMPLETED;
      default:
        return Intent.UNKNOWN;
    }
  }
}

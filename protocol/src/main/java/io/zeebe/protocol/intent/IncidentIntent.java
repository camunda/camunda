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

public enum IncidentIntent implements Intent {
  CREATE((short) 0),
  CREATED((short) 1),

  RESOLVE((short) 2),
  RESOLVED((short) 3),
  RESOLVE_FAILED((short) 4),

  DELETE((short) 5),
  DELETED((short) 6);

  private short value;

  IncidentIntent(short value) {
    this.value = value;
  }

  public short getIntent() {
    return value;
  }

  public static Intent from(short value) {
    switch (value) {
      case 0:
        return CREATE;
      case 1:
        return CREATED;
      case 2:
        return RESOLVE;
      case 3:
        return RESOLVED;
      case 4:
        return RESOLVE_FAILED;
      case 5:
        return DELETE;
      case 6:
        return DELETED;
      default:
        return Intent.UNKNOWN;
    }
  }

  @Override
  public short value() {
    return value;
  }
}

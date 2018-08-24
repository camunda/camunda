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

public enum DeploymentIntent implements Intent {
  CREATE((short) 0),
  DISTRIBUTE((short) 1),
  CREATING((short) 2),
  CREATED((short) 3);

  private short value;

  DeploymentIntent(short value) {
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
        return DISTRIBUTE;
      case 2:
        return CREATING;
      case 3:
        return CREATED;
      default:
        return Intent.UNKNOWN;
    }
  }

  @Override
  public short value() {
    return value;
  }
}

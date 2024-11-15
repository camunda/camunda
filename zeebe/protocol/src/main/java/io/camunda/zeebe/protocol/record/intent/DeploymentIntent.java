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

public enum DeploymentIntent implements Intent {
  CREATE((short) 0),
  CREATED((short) 1),

  /**
   * Intent related to distribution are deprecated as of 8.3.0. A generalised way of distributing
   * commands has been introduced in this version. The DeploymentCreateProcessor is now using this
   * new way. This intent only remains to stay backwards compatible.
   */
  @Deprecated
  DISTRIBUTE((short) 2),
  @Deprecated
  DISTRIBUTED((short) 3),
  @Deprecated
  FULLY_DISTRIBUTED((short) 4),

  RECONSTRUCT((short) 5),
  RECONSTRUCTED((short) 6);

  private final short value;

  DeploymentIntent(final short value) {
    this.value = value;
  }

  public short getIntent() {
    return value;
  }

  public static Intent from(final short value) {
    switch (value) {
      case 0:
        return CREATE;
      case 1:
        return CREATED;
      case 2:
        return DISTRIBUTE;
      case 3:
        return DISTRIBUTED;
      case 4:
        return FULLY_DISTRIBUTED;
      case 5:
        return RECONSTRUCT;
      case 6:
        return RECONSTRUCTED;
      default:
        return UNKNOWN;
    }
  }

  @Override
  public short value() {
    return value;
  }

  @Override
  public boolean isEvent() {
    switch (this) {
      case CREATED:
      case DISTRIBUTED:
      case FULLY_DISTRIBUTED:
      case RECONSTRUCTED:
        return true;
      default:
        return false;
    }
  }
}

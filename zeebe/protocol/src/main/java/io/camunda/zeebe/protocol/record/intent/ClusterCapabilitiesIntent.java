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

/**
 * Intents of the commit of the cluster's enabled capabilities to one partition. The partition
 * leader writes the command when its engine state does not contain all capabilities that the
 * cluster enabled.
 */
public enum ClusterCapabilitiesIntent implements Intent {

  /** Adds the capabilities of the record to the enabled capabilities of the partition. */
  COMMIT(0),

  /** The enabled capabilities of the partition are now the capabilities of the record. */
  COMMITTED(1);

  private final short value;

  ClusterCapabilitiesIntent(final int value) {
    this.value = (short) value;
  }

  public static Intent from(final short value) {
    switch (value) {
      case 0:
        return COMMIT;
      case 1:
        return COMMITTED;
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
    return this == COMMITTED;
  }
}

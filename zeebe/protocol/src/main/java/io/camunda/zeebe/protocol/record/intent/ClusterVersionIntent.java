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

public enum ClusterVersionIntent implements Intent {
  RAISE(0),
  APPLIED(1),
  /** Demo "new feature" command — gated at the admission layer by {@link ClusterVersionIntent}. */
  PING(2),
  PINGED(3),
  /** Second demo command, gated at a lower ordinal than PING. */
  ECHO(4),
  ECHOED(5),
  /** Disable a specific behavior flag (rollback-lite) — see ClusterVersionFeatures. */
  SUPPRESS_FLAG(6),
  FLAG_SUPPRESSED(7),
  UNSUPPRESS_FLAG(8),
  FLAG_UNSUPPRESSED(9);

  private final short value;

  ClusterVersionIntent(final int value) {
    this.value = (short) value;
  }

  @Override
  public short value() {
    return value;
  }

  @Override
  public boolean isEvent() {
    return this == APPLIED
        || this == PINGED
        || this == ECHOED
        || this == FLAG_SUPPRESSED
        || this == FLAG_UNSUPPRESSED;
  }

  public static Intent from(final short value) {
    switch (value) {
      case 0:
        return RAISE;
      case 1:
        return APPLIED;
      case 2:
        return PING;
      case 3:
        return PINGED;
      case 4:
        return ECHO;
      case 5:
        return ECHOED;
      case 6:
        return SUPPRESS_FLAG;
      case 7:
        return FLAG_SUPPRESSED;
      case 8:
        return UNSUPPRESS_FLAG;
      case 9:
        return FLAG_UNSUPPRESSED;
      default:
        return UNKNOWN;
    }
  }
}

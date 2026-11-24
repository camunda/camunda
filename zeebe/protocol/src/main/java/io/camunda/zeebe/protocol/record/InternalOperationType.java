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
package io.camunda.zeebe.protocol.record;

/**
 * The kind of internal operation {@link Record#getInternalOperationReference()} points to.
 *
 * <p>The log and {@link Record#getInternalOperationType()} store the {@link #code()}, not the enum:
 * a reader built against an older version then keeps a code it does not know instead of failing to
 * map it. {@link #fromCode(int)} maps such a code to {@link #UNKNOWN}. Never reuse or renumber a
 * code, it is persisted in the log.
 */
public enum InternalOperationType {

  /** No internal operation, or the record was written before the type existed. */
  UNSPECIFIED(0),

  /** The reference is the key of a policy update. */
  POLICY_UPDATE(1),

  /**
   * A code this version does not know, written by a newer version. It is never written: a record
   * with an unknown code keeps that code when it is passed on.
   */
  UNKNOWN(-1);

  private final int code;

  InternalOperationType(final int code) {
    this.code = code;
  }

  /** The code that is stored in the log for this type. */
  public int code() {
    return code;
  }

  /**
   * Maps a stored code to its type.
   *
   * @return the type of the code, or {@link #UNKNOWN} for a code this version does not know
   */
  public static InternalOperationType fromCode(final int code) {
    for (final InternalOperationType type : values()) {
      if (type != UNKNOWN && type.code == code) {
        return type;
      }
    }
    return UNKNOWN;
  }
}

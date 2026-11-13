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
 * The lifecycle of a probe: a short request from a client to a worker, answered within the client's
 * request. The names follow the job vocabulary. {@code ANSWERED}, {@code TIMED_OUT} and {@code
 * CANCELED} end the probe; the engine writes {@code CANCELED} without a command.
 */
public enum ProbeIntent implements Intent {
  CREATE((short) 0),
  CREATED((short) 1),

  YIELD((short) 2),
  YIELDED((short) 3),

  ANSWER((short) 4),
  ANSWERED((short) 5),

  TIME_OUT((short) 6),
  TIMED_OUT((short) 7),

  CANCELED((short) 8);

  private final short value;

  ProbeIntent(final short value) {
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
        return YIELD;
      case 3:
        return YIELDED;
      case 4:
        return ANSWER;
      case 5:
        return ANSWERED;
      case 6:
        return TIME_OUT;
      case 7:
        return TIMED_OUT;
      case 8:
        return CANCELED;
      default:
        return Intent.UNKNOWN;
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
      case YIELDED:
      case ANSWERED:
      case TIMED_OUT:
      case CANCELED:
        return true;
      default:
        return false;
    }
  }
}

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
 * Lifecycle of a probe: a short request from a client to a worker that the client waits for. The
 * names follow the job vocabulary; parking for secrets has no probe event, as for jobs.
 */
public enum ProbeIntent implements Intent {
  CREATE(0, false),
  CREATED(1, true),
  YIELD(3, false),
  YIELDED(4, true),
  ANSWER(5, false),
  ANSWERED(6, true),
  TIME_OUT(7, false),
  TIMED_OUT(8, true),
  CANCELED(9, true),
  /**
   * Internal command appended by ANSWER, which can only answer one requester (the worker). Its
   * processor answers the waiting client; it writes no probe event.
   */
  RESPOND(10, false);

  private final short value;
  private final boolean isEvent;

  ProbeIntent(final int value, final boolean isEvent) {
    this.value = (short) value;
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

  public static Intent from(final short value) {
    for (final ProbeIntent intent : values()) {
      if (intent.value == value) {
        return intent;
      }
    }
    return Intent.UNKNOWN;
  }
}

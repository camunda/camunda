/*
 * Copyright 2014-present Open Networking Foundation
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atomix.utils.event;

import static com.google.common.base.MoreObjects.toStringHelper;

import io.atomix.utils.misc.TimestampPrinter;

/** Base event implementation. */
public class AbstractEvent<T extends Enum, S> implements Event<T, S> {
  private final long time;
  private final T type;
  private final S subject;

  /**
   * Creates an event of a given type and for the specified subject and the current time.
   *
   * @param type event type
   * @param subject event subject
   */
  protected AbstractEvent(final T type, final S subject) {
    this(type, subject, System.currentTimeMillis());
  }

  /**
   * Creates an event of a given type and for the specified subject and time.
   *
   * @param type event type
   * @param subject event subject
   * @param time occurrence time
   */
  protected AbstractEvent(final T type, final S subject, final long time) {
    this.type = type;
    this.subject = subject;
    this.time = time;
  }

  @Override
  public long time() {
    return time;
  }

  @Override
  public T type() {
    return type;
  }

  @Override
  public S subject() {
    return subject;
  }

  @Override
  public String toString() {
    return toStringHelper(this)
        .add("time", new TimestampPrinter(time))
        .add("type", type())
        .add("subject", subject())
        .toString();
  }
}

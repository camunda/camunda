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

/** Abstraction of an of a time-stamped event pertaining to an arbitrary subject. */
public interface Event<T, S> {

  /**
   * Returns the timestamp of when the event occurred, given in milliseconds since the start of
   * epoch.
   *
   * @return timestamp in milliseconds
   */
  long time();

  /**
   * Returns the type of the event.
   *
   * @return event type
   */
  T type();

  /**
   * Returns the subject of the event.
   *
   * @return subject to which this event pertains
   */
  S subject();
}

/*
 * Copyright 2018-present Open Networking Foundation
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
package io.atomix.primitive.protocol.set;

import io.atomix.utils.event.AbstractEvent;

/** Set protocol event. */
public class SetDelegateEvent<E> extends AbstractEvent<SetDelegateEvent.Type, E> {

  public SetDelegateEvent(final Type type, final E element) {
    super(type, element);
  }

  public SetDelegateEvent(final Type type, final E element, final long time) {
    super(type, element, time);
  }

  /**
   * Returns the set element.
   *
   * @return the set element
   */
  public E element() {
    return subject();
  }

  /** Set protocol event type. */
  public enum Type {
    /** Element added to set. */
    ADD,

    /** Element removed from the set. */
    REMOVE,
  }
}

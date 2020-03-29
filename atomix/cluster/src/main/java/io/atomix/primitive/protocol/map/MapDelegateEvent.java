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
package io.atomix.primitive.protocol.map;

import io.atomix.utils.event.AbstractEvent;

/** Map protocol event. */
public class MapDelegateEvent<K, V> extends AbstractEvent<MapDelegateEvent.Type, K> {

  private final V value;

  public MapDelegateEvent(final Type type, final K key, final V value) {
    super(type, key);
    this.value = value;
  }

  public MapDelegateEvent(final Type type, final K key, final V value, final long time) {
    super(type, key, time);
    this.value = value;
  }

  /**
   * Returns the map entry key.
   *
   * @return the map entry key
   */
  public K key() {
    return subject();
  }

  /**
   * Returns the map entry value.
   *
   * @return the map entry value
   */
  public V value() {
    return value;
  }

  /** Map protocol event type. */
  public enum Type {
    /** Entry added to map. */
    INSERT,

    /** Existing entry updated. */
    UPDATE,

    /** Entry removed from map. */
    REMOVE
  }
}

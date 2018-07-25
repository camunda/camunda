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
package io.zeebe.client.impl.subscription.topic;

import java.lang.reflect.Array;

public class BiEnumMap<K1 extends Enum<K1>, K2 extends Enum<K2>, V> {
  private final V[] elements;
  private final int key2Cardinality;

  @SuppressWarnings("unchecked")
  public BiEnumMap(Class<K1> key1Class, Class<K2> key2Class, Class<?> valueClass) {
    final int key1Cardinality = key1Class.getEnumConstants().length;
    this.key2Cardinality = key2Class.getEnumConstants().length;
    final int cardinality = key1Cardinality * key2Cardinality;

    this.elements = (V[]) Array.newInstance(valueClass, cardinality);
  }

  private int mapToIndex(K1 key1, K2 key2) {
    return (key1.ordinal() * key2Cardinality) + key2.ordinal();
  }

  public V get(K1 key1, K2 key2) {
    return elements[mapToIndex(key1, key2)];
  }

  public void put(K1 key1, K2 key2, V value) {
    elements[mapToIndex(key1, key2)] = value;
  }
}

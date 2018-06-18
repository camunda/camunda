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
package io.zeebe.test.util.collection;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

public class MapFactoryBuilder<A, T> {
  protected T returnValue;
  protected Consumer<Function<A, Map<String, Object>>> factoryCallback;

  protected BiConsumer<A, Map<String, Object>> manipulationChain = (a, m) -> {};

  public MapFactoryBuilder(
      T returnValue, Consumer<Function<A, Map<String, Object>>> factoryCallback) {
    this.returnValue = returnValue;
    this.factoryCallback = factoryCallback;
  }

  public MapFactoryBuilder<A, T> allOf(Function<A, Map<String, Object>> otherMap) {
    manipulationChain = manipulationChain.andThen((a, m) -> m.putAll(otherMap.apply(a)));
    return this;
  }

  public MapFactoryBuilder<A, T> put(String key, Object value) {
    manipulationChain = manipulationChain.andThen((a, m) -> m.put(key, value));
    return this;
  }

  public MapFactoryBuilder<A, T> put(String key, Function<A, Object> valueFunction) {
    manipulationChain = manipulationChain.andThen((a, m) -> m.put(key, valueFunction.apply(a)));
    return this;
  }

  public T done() {
    factoryCallback.accept(
        (a) -> {
          final Map<String, Object> map = new HashMap<>();
          manipulationChain.accept(a, map);
          return map;
        });
    return returnValue;
  }
}

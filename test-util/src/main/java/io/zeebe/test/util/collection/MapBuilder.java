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
import java.util.function.Consumer;

public class MapBuilder<T> {
  protected T returnValue;
  protected Consumer<Map<String, Object>> mapCallback;

  protected Map<String, Object> map;

  public MapBuilder(T returnValue, Consumer<Map<String, Object>> mapCallback) {
    this.returnValue = returnValue;
    this.mapCallback = mapCallback;
    this.map = new HashMap<>();
  }

  public MapBuilder<T> putAll(Map<String, Object> map) {
    this.map.putAll(map);
    return this;
  }

  public MapBuilder<T> put(String key, Object value) {
    this.map.put(key, value);
    return this;
  }

  public T done() {
    mapCallback.accept(map);
    return returnValue;
  }
}

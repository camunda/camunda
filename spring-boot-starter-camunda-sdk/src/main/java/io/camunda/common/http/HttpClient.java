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
package io.camunda.common.http;

import com.google.common.reflect.TypeToken;
import io.camunda.common.auth.Product;
import java.util.Map;

/** Interface to enable swappable http client implementations */
public interface HttpClient {

  void init(String host, String basePath);

  void loadMap(Product product, Map<Class<?>, String> map);

  <T> T get(Class<T> responseType, Long key);

  <T> T get(Class<T> responseType, String id);

  <T, V, W> T get(Class<T> responseType, Class<V> parameterType, TypeToken<W> selector, Long key);

  <T> String getXml(Class<T> selector, Long key);

  <T, V, W, U> T post(Class<T> responseType, Class<V> parameterType, TypeToken<W> selector, U body);

  <T, V> T delete(Class<T> responseType, Class<V> selector, Long key);
}

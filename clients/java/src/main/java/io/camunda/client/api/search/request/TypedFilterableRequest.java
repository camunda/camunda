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
package io.camunda.client.api.search.request;

import java.util.function.Consumer;

public interface TypedFilterableRequest<F, SELF extends TypedFilterableRequest<F, SELF>> {

  /**
   * Sets the filter to be included in the search request. Invoking the method overrides previously
   * set filters.
   *
   * @param value the filter
   * @return the builder for the search request
   */
  SELF filter(final F value);

  /**
   * Provides a fluent builder to create a filter to be included in the search request. Invoking the
   * method overrides previously set filters. You can chain multiple filter criteria inside the
   * consumer you provide for such cases.
   *
   * @param fn consumer to create the filter
   * @return the builder for the search request
   */
  SELF filter(final Consumer<F> fn);

  interface SearchRequestFilter {}
}

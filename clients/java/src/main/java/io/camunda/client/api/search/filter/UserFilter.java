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
package io.camunda.client.api.search.filter;

import io.camunda.client.api.search.filter.builder.StringProperty;
import io.camunda.client.api.search.request.TypedFilterableRequest.SearchRequestFilter;
import java.util.function.Consumer;

public interface UserFilter extends SearchRequestFilter {

  /**
   * Filter users by the specified username.
   *
   * @param username the username of the user
   * @return the updated filter
   */
  UserFilter username(final String username);

  /**
   * Filters users by the specified username using {@link StringProperty} consumer.
   *
   * @param fn the username {@link StringProperty} consumer of the user
   * @return the updated filter
   */
  UserFilter username(final Consumer<StringProperty> fn);

  /**
   * Filter users by the specified name.
   *
   * @param name the name of the user
   * @return the updated filter
   */
  UserFilter name(final String name);

  /**
   * Filters users by the specified name using {@link StringProperty} consumer.
   *
   * @param fn the name {@link StringProperty} consumer of the user
   * @return the updated filter
   */
  UserFilter name(final Consumer<StringProperty> fn);

  /**
   * Filter users by the specified email.
   *
   * @param email the email of the user
   * @return the updated filter
   */
  UserFilter email(final String email);

  /**
   * Filters users by the specified email using {@link StringProperty} consumer.
   *
   * @param fn the email {@link StringProperty} consumer of the user
   * @return the updated filter
   */
  UserFilter email(final Consumer<StringProperty> fn);
}

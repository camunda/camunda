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

import io.camunda.client.api.command.ListSecretsCommandStep1;
import io.camunda.client.api.search.enums.SecretStatus;
import io.camunda.client.api.search.filter.builder.SecretStatusProperty;
import java.util.function.Consumer;

/** Narrows the secret references a {@link ListSecretsCommandStep1} lists. */
public interface SecretListFilter {

  /**
   * Only lists the secrets in the given state.
   *
   * @param status the state to list
   * @return the updated filter
   */
  SecretListFilter status(SecretStatus status);

  /**
   * Only lists the secrets whose state matches the given operators, for example {@code s ->
   * s.in(SecretStatus.DISABLED, SecretStatus.EXPIRED)}.
   *
   * @param fn the consumer that sets the status operators
   * @return the updated filter
   */
  SecretListFilter status(Consumer<SecretStatusProperty> fn);
}

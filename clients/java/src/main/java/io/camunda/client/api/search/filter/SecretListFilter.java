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

/** Narrows the secret references a {@link ListSecretsCommandStep1} lists. */
public interface SecretListFilter {

  /**
   * Only lists the secrets in the given state. Requires a cluster on 8.11 or later.
   *
   * @param status the state to list, or {@code null} to list secrets in every state
   * @return the updated filter
   */
  SecretListFilter status(SecretStatus status);
}

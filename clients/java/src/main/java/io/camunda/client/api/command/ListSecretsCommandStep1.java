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
package io.camunda.client.api.command;

import io.camunda.client.api.response.ListSecretsResponse;
import io.camunda.client.api.search.filter.SecretListFilter;
import java.time.Duration;
import java.util.function.Consumer;

/**
 * Command to list the secret reference names the caller is authorized to see. Without a filter, it
 * lists the secrets in every state.
 */
public interface ListSecretsCommandStep1 extends FinalCommandStep<ListSecretsResponse> {

  /**
   * Narrows the listed references, for example to the expired secrets, or with the advanced
   * operators to the secrets that are disabled or expired:
   *
   * <pre>
   *   camundaClient
   *       .newListSecretsCommand()
   *       .filter(f -&gt; f.status(SecretStatus.EXPIRED))
   *       .send();
   *
   *   camundaClient
   *       .newListSecretsCommand()
   *       .filter(f -&gt; f.status(s -&gt; s.in(SecretStatus.DISABLED, SecretStatus.EXPIRED)))
   *       .send();
   * </pre>
   *
   * <p>Supported since 8.11: an older cluster rejects a request that sets a criterion with a {@code
   * 400} response, which completes the command with a {@link ProblemException}. A filter without
   * criteria is not sent. Each call replaces the previous filter.
   *
   * @param fn the consumer that sets the filter criteria
   * @return the builder for this command
   */
  ListSecretsCommandStep1 filter(Consumer<SecretListFilter> fn);

  @Override
  ListSecretsCommandStep1 requestTimeout(Duration requestTimeout);
}

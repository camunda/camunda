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

import io.camunda.client.api.response.ResolveSecretsResponse;
import java.time.Duration;
import java.util.List;

/**
 * Command to resolve a batch of secret references in a single round-trip.
 *
 * <p>At least one reference must be set before the command is sent. Duplicate references are
 * deduplicated by the cluster, and the cluster also rejects a batch that exceeds its maximum size,
 * so neither is enforced here.
 */
public interface ResolveSecretsCommandStep1 extends FinalCommandStep<ResolveSecretsResponse> {

  /**
   * Sets the references to resolve, replacing any previously set references.
   *
   * @param references the secret references to resolve
   * @return the builder for this command
   */
  ResolveSecretsCommandStep1 references(List<String> references);

  /**
   * Sets the references to resolve, replacing any previously set references.
   *
   * @param references the secret references to resolve
   * @return the builder for this command
   */
  ResolveSecretsCommandStep1 references(String... references);

  /**
   * Adds a single reference to resolve, keeping any previously set references.
   *
   * @param reference the secret reference to resolve
   * @return the builder for this command
   */
  ResolveSecretsCommandStep1 reference(String reference);

  @Override
  ResolveSecretsCommandStep1 requestTimeout(Duration requestTimeout);
}

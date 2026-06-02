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

import io.camunda.client.api.response.SecretResolveResponse;
import java.util.List;

/**
 * Fluent command to resolve a batch of {@code camunda.secrets.*} references against the gateway's
 * secret resolution endpoint. Only successfully resolved entries are returned; missing or malformed
 * references are silently omitted from the response.
 *
 * <pre>{@code
 * Map<String, String> resolved = camundaClient
 *     .newSecretResolveCommand()
 *     .references("camunda.secrets.STRIPE_API_KEY", "camunda.secrets.WEBHOOK_SECRET")
 *     .send()
 *     .join()
 *     .getResolved();
 * }</pre>
 */
public interface SecretResolveCommandStep1 extends FinalCommandStep<SecretResolveResponse> {

  /** Adds references to resolve. May be called multiple times; subsequent calls accumulate. */
  SecretResolveCommandStep1 references(String... references);

  /** Variant accepting a list. May be called multiple times; subsequent calls accumulate. */
  SecretResolveCommandStep1 references(List<String> references);
}

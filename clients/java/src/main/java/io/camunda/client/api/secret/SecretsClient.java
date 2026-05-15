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
package io.camunda.client.api.secret;

import io.camunda.client.CamundaClient;
import java.util.List;
import java.util.Map;

/**
 * Resolves {@code camunda.secrets.*} references against the gateway's secret resolution endpoint.
 *
 * <p>Implementations are expected to return only successfully resolved entries; missing references
 * are omitted from the result.
 *
 * <p>For most use cases, callers do not implement this interface directly. The default
 * implementation provided by {@link #fromCamundaClient(CamundaClient)} delegates to {@link
 * CamundaClient#newSecretResolveCommand()}, reusing the client's HTTP transport and authentication.
 * Custom implementations are useful for testing (in-memory stub) or alternative backends.
 */
public interface SecretsClient {

  Map<String, String> resolve(List<String> references);

  /**
   * Default adapter that delegates resolution to {@link CamundaClient#newSecretResolveCommand()}.
   */
  static SecretsClient fromCamundaClient(final CamundaClient client) {
    return references ->
        client.newSecretResolveCommand().references(references).send().join().getResolved();
  }
}

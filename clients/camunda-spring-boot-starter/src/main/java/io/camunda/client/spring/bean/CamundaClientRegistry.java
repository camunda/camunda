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
package io.camunda.client.spring.bean;

import io.camunda.client.CamundaClient;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Provides access to the {@link CamundaClient} instances configured under {@code
 * camunda.clients.<name>.*} by their configured name.
 *
 * <p>Each configured client is also registered as an individual Spring bean named {@code
 * <name>CamundaClient} (see the multi-client auto-configuration), so clients can additionally be
 * injected directly with {@code @Qualifier}. The client marked {@code primary} — or the sole client
 * when only one is configured — is the {@code @Primary} bean and is returned by {@link
 * #getPrimary()}.
 *
 * <pre>{@code
 * @Autowired CamundaClientRegistry registry;
 *
 * CamundaClient finance = registry.get("finance");
 * CamundaClient primary = registry.getPrimary();
 * }</pre>
 */
public interface CamundaClientRegistry {

  /**
   * Returns the client configured under the given name.
   *
   * @throws IllegalArgumentException if no client is configured under {@code clientName}
   */
  CamundaClient get(String clientName);

  /** Returns the client configured under the given name, if any. */
  Optional<CamundaClient> find(String clientName);

  /**
   * Returns the primary (default) client.
   *
   * @throws IllegalStateException if no primary client is designated (more than one client is
   *     configured and none is marked {@code primary})
   */
  CamundaClient getPrimary();

  /** Returns the names of all configured clients. */
  Set<String> clientNames();

  /** Returns all configured clients keyed by name. */
  Map<String, CamundaClient> all();
}

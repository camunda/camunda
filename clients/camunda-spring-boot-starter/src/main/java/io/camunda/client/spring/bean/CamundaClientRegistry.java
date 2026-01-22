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
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A registry that provides access to all configured {@link CamundaClient} instances by name.
 *
 * <p>When using multi-client configuration, this registry allows looking up clients by their
 * configured name. For example, if you have configured:
 *
 * <pre>
 * camunda:
 *   clients:
 *     production:
 *       rest-address: https://prod.camunda.io
 *     staging:
 *       rest-address: https://staging.camunda.io
 * </pre>
 *
 * You can then use:
 *
 * <pre>
 * &#64;Autowired
 * private CamundaClientRegistry registry;
 *
 * public void doSomething() {
 *     CamundaClient prodClient = registry.getClient("production");
 *     CamundaClient stagingClient = registry.getClient("staging");
 * }
 * </pre>
 */
public class CamundaClientRegistry {

  private final Map<String, CamundaClient> clients;

  public CamundaClientRegistry(final Map<String, CamundaClient> clients) {
    this.clients = clients != null ? Map.copyOf(clients) : Collections.emptyMap();
  }

  /**
   * Returns the {@link CamundaClient} with the specified name.
   *
   * @param name the name of the client as configured in properties
   * @return the client instance
   * @throws IllegalArgumentException if no client with the given name exists
   */
  public CamundaClient getClient(final String name) {
    final CamundaClient client = clients.get(name);
    if (client == null) {
      throw new IllegalArgumentException(
          "No CamundaClient found with name '"
              + name
              + "'. Available clients: "
              + clients.keySet());
    }
    return client;
  }

  /**
   * Returns the {@link CamundaClient} with the specified name, if it exists.
   *
   * @param name the name of the client as configured in properties
   * @return an Optional containing the client, or empty if not found
   */
  public Optional<CamundaClient> findClient(final String name) {
    return Optional.ofNullable(clients.get(name));
  }

  /**
   * Returns the names of all registered clients.
   *
   * @return an unmodifiable set of client names
   */
  public Set<String> getClientNames() {
    return clients.keySet();
  }

  /**
   * Returns all registered clients.
   *
   * @return an unmodifiable map of client names to client instances
   */
  public Map<String, CamundaClient> getAllClients() {
    return clients;
  }

  /**
   * Checks if a client with the specified name exists.
   *
   * @param name the name to check
   * @return true if a client with this name exists
   */
  public boolean hasClient(final String name) {
    return clients.containsKey(name);
  }

  /**
   * Returns the number of registered clients.
   *
   * @return the client count
   */
  public int size() {
    return clients.size();
  }
}

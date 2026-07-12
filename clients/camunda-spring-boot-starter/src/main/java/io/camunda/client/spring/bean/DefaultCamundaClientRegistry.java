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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Default {@link CamundaClientRegistry} that resolves clients lazily, on demand: it holds the
 * mapping of configured client name to Spring bean name and looks the client bean up only when it
 * is actually requested. This preserves the per-client beans' lazy initialization — injecting the
 * registry does not force every client to be created.
 */
public class DefaultCamundaClientRegistry implements CamundaClientRegistry {

  private final Map<String, String> beanNamesByClientName;
  private final Function<String, CamundaClient> clientByBeanName;
  private final String primaryClientName;

  /**
   * @param beanNamesByClientName the configured client name to registered bean name mapping
   * @param clientByBeanName resolves a {@link CamundaClient} bean by its bean name (e.g. via the
   *     bean factory); invoked lazily on lookup
   * @param primaryClientName the name of the primary client, or {@code null} if none is designated
   */
  public DefaultCamundaClientRegistry(
      final Map<String, String> beanNamesByClientName,
      final Function<String, CamundaClient> clientByBeanName,
      final String primaryClientName) {
    this.beanNamesByClientName =
        Collections.unmodifiableMap(new LinkedHashMap<>(beanNamesByClientName));
    this.clientByBeanName = clientByBeanName;
    this.primaryClientName = primaryClientName;
  }

  @Override
  public CamundaClient get(final String clientName) {
    final String beanName = beanNamesByClientName.get(clientName);
    if (beanName == null) {
      throw new IllegalArgumentException(
          String.format(
              "No CamundaClient configured under name '%s'. Available clients: %s",
              clientName, beanNamesByClientName.keySet()));
    }
    return clientByBeanName.apply(beanName);
  }

  @Override
  public Optional<CamundaClient> find(final String clientName) {
    return beanNamesByClientName.containsKey(clientName)
        ? Optional.of(get(clientName))
        : Optional.empty();
  }

  @Override
  public CamundaClient getPrimary() {
    if (primaryClientName == null) {
      throw new IllegalStateException(
          String.format(
              "No primary CamundaClient is designated. Mark one client with "
                  + "'camunda.clients.<name>.primary=true'. Configured clients: %s",
              beanNamesByClientName.keySet()));
    }
    return get(primaryClientName);
  }

  @Override
  public Set<String> clientNames() {
    return beanNamesByClientName.keySet();
  }

  @Override
  public Map<String, CamundaClient> all() {
    final Map<String, CamundaClient> clients = new LinkedHashMap<>();
    beanNamesByClientName.keySet().forEach(name -> clients.put(name, get(name)));
    return Collections.unmodifiableMap(clients);
  }
}

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
package io.camunda.client.spring.properties;

import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for multiple Camunda clients.
 *
 * <p>Example configuration:
 *
 * <pre>
 * camunda:
 *   clients:
 *     production:
 *       primary: true
 *       rest-address: https://prod.camunda.io
 *       auth:
 *         client-id: prod-client
 *     staging:
 *       rest-address: https://staging.camunda.io
 *       auth:
 *         client-id: staging-client
 * </pre>
 */
@ConfigurationProperties("camunda")
public class MultiCamundaClientProperties {

  /**
   * A map of named Camunda client configurations. Each entry represents a separate client instance
   * that will be registered as a Spring bean with the key as part of its bean name.
   */
  private Map<String, CamundaClientConfigurationProperties> clients = new HashMap<>();

  public Map<String, CamundaClientConfigurationProperties> getClients() {
    return clients;
  }

  public void setClients(final Map<String, CamundaClientConfigurationProperties> clients) {
    this.clients = clients;
  }

  /**
   * Checks if multi-client configuration is enabled.
   *
   * @return true if at least one client is configured in the clients map
   */
  public boolean isMultiClientEnabled() {
    return clients != null && !clients.isEmpty();
  }

  @Override
  public String toString() {
    return "MultiCamundaClientProperties{" + "clients=" + clients + '}';
  }
}

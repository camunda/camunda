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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The resolved generic multi-client configuration: one fully-resolved {@link
 * CamundaClientProperties} per named client configured under {@code camunda.clients.<name>.*}, plus
 * the designated primary client name.
 *
 * <p>This is a plain data holder. The binding, overlay onto the shared {@code camunda.client.*}
 * base, validation, and primary resolution are performed by {@link
 * MultiCamundaClientPropertiesResolver}.
 */
public final class MultiCamundaClientProperties {

  private final Map<String, CamundaClientProperties> clients;
  private final String primaryClientName;

  public MultiCamundaClientProperties(
      final Map<String, CamundaClientProperties> clients, final String primaryClientName) {
    this.clients = Collections.unmodifiableMap(new LinkedHashMap<>(clients));
    this.primaryClientName = primaryClientName;
  }

  /** The resolved clients keyed by their configured name. */
  public Map<String, CamundaClientProperties> getClients() {
    return clients;
  }

  /**
   * The name of the primary (default) client, if one is designated: the entry marked {@code
   * primary=true}, or the sole entry when only one client is configured. Empty when several clients
   * are configured and none is marked primary.
   */
  public Optional<String> getPrimaryClientName() {
    return Optional.ofNullable(primaryClientName);
  }
}

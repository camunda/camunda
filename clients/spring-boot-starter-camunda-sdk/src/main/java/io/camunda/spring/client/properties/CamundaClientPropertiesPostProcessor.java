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
package io.camunda.spring.client.properties;

import io.camunda.spring.client.properties.CamundaClientProperties.ClientMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

public class CamundaClientPropertiesPostProcessor implements EnvironmentPostProcessor {
  private static final Logger LOG =
      LoggerFactory.getLogger(CamundaClientPropertiesPostProcessor.class);
  private static final String OVERRIDE_PREFIX = "camunda.client.worker.override.";
  private static final List<String> LEGACY_OVERRIDE_PREFIX =
      List.of("camunda.client.zeebe.override.", "zeebe.client.worker.override.");

  @Override
  public void postProcessEnvironment(
      final ConfigurableEnvironment environment, final SpringApplication application) {
    mapLegacyProperties(environment);
    mapLegacyOverrides(environment);
    processClientMode(environment);
  }

  private void mapLegacyOverrides(final ConfigurableEnvironment environment) {
    final Map<String, Object> properties = new HashMap<>();
    environment.getPropertySources().stream()
        .filter(o -> EnumerablePropertySource.class.isAssignableFrom(o.getClass()))
        .map(EnumerablePropertySource.class::cast)
        .forEach(
            propertySource -> {
              for (final String propertyName : propertySource.getPropertyNames()) {
                for (final String prefix : LEGACY_OVERRIDE_PREFIX) {
                  final String normalizedPropertyName =
                      propertyName.replaceAll("_", ".").toLowerCase();
                  if (normalizedPropertyName.startsWith(prefix)) {
                    final String newPropertyName =
                        OVERRIDE_PREFIX + normalizedPropertyName.substring(prefix.length());
                    if (!properties.containsKey(newPropertyName)
                        && !environment.containsProperty(newPropertyName)) {
                      LOG.debug(
                          "Mapping worker override from '{}' to '{}'",
                          propertyName,
                          newPropertyName);
                      properties.put(newPropertyName, propertySource.getProperty(propertyName));
                    }
                  }
                }
              }
            });
    final PropertySource<?> propertySource =
        new MapPropertySource("camunda-client-legacy-worker-overrides.properties", properties);
    environment.getPropertySources().addFirst(propertySource);
  }

  private void mapLegacyProperties(final ConfigurableEnvironment environment) {
    final Map<String, Object> properties = new HashMap<>();
    final List<CamundaClientLegacyPropertiesMapping> mappings =
        CamundaClientLegacyPropertiesMappingsLoader.load();
    mappings.forEach(
        mapping ->
            detectPropertyValue(environment, mapping)
                .forEach(
                    detectedPropertyValue ->
                        properties.put(mapping.getPropertyName(), detectedPropertyValue)));
    final PropertySource<?> propertySource =
        new MapPropertySource("camunda-client-legacy-overrides.properties", properties);
    environment.getPropertySources().addFirst(propertySource);
  }

  private List<String> detectPropertyValue(
      final ConfigurableEnvironment environment,
      final CamundaClientLegacyPropertiesMapping property) {
    if (environment.containsProperty(property.getPropertyName())) {
      LOG.debug(
          "Property '{}' found, not looking up legacy properties", property.getPropertyName());
      return List.of(Objects.requireNonNull(environment.getProperty(property.getPropertyName())));
    }
    for (final String legacyPropertyName : property.getLegacyPropertyNames()) {
      if (environment.containsProperty(legacyPropertyName)) {
        LOG.warn(
            "Legacy property '{}' found, setting to '{}'. Please update your setup to use the latest property",
            legacyPropertyName,
            property.getPropertyName());
        return List.of(Objects.requireNonNull(environment.getProperty(legacyPropertyName)));
      }
    }
    LOG.debug("No property found for '{}'", property.getPropertyName());
    return List.of();
  }

  private void processClientMode(final ConfigurableEnvironment environment) {
    try {
      ClientMode clientMode = environment.getProperty("camunda.client.mode", ClientMode.class);
      if (clientMode == null) {
        if (isImplicitSaas(environment)) {
          clientMode = ClientMode.saas;
        } else {
          return;
        }
      }
      final YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
      final String propertiesFile = determinePropertiesFile(clientMode);
      final ClassPathResource resource = new ClassPathResource(propertiesFile);
      final List<PropertySource<?>> props = loader.load(propertiesFile, resource);
      for (final PropertySource<?> prop : props) {
        environment.getPropertySources().addLast(prop); // lowest priority
      }
    } catch (final Exception e) {
      throw new IllegalStateException("Error while post processing camunda properties", e);
    }
  }

  private boolean isImplicitSaas(final ConfigurableEnvironment environment) {
    if (environment.containsProperty("camunda.client.cloud.cluster-id")) {
      LOG.debug("Cluster id detected, setting implicit saas mode");
      return true;
    }
    return false;
  }

  private String determinePropertiesFile(final ClientMode clientMode) {
    switch (clientMode) {
      case selfManaged -> {
        return "modes/self-managed.yaml";
      }
      case saas -> {
        return "modes/saas.yaml";
      }
      case basic -> {
        return "modes/basic.yaml";
      }
      default -> throw new IllegalStateException("Unknown client mode " + clientMode);
    }
  }
}

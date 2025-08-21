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
package io.camunda.zeebe.spring.client.properties;

import io.camunda.zeebe.spring.client.properties.CamundaClientProperties.ClientMode;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.logging.DeferredLog;
import org.springframework.boot.logging.DeferredLogFactory;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

public class PropertiesPostProcessor implements EnvironmentPostProcessor {
  public static final String CAMUNDA_CLIENT_MODE = "camunda.client.mode";

  private static final String OVERRIDE_PREFIX = "camunda.client.zeebe.override.";
  private static final List<String> LEGACY_OVERRIDE_PREFIX =
      List.of("zeebe.client.worker.override.");
  private final DeferredLog log;

  public PropertiesPostProcessor(final DeferredLogFactory deferredLogFactory) {
    log = (DeferredLog) deferredLogFactory.getLog(getClass());
  }

  @Override
  public void postProcessEnvironment(
      final ConfigurableEnvironment environment, final SpringApplication application) {
    mapLegacyProperties(environment);
    mapLegacyOverrides(environment);
    processClientMode(environment);
    createMessages(environment);
  }

  private void createMessages(final ConfigurableEnvironment environment) {
    if (!environment.containsProperty("camunda.client.zeebe.prefer-rest-over-grpc")) {
      log.warn(
          "No 'camunda.client.zeebe.prefer-rest-over-grpc' is set. Please set to 'true' or 'false' explicitly as the default behaviour will change in the next release.");
    }
  }

  private void mapLegacyOverrides(final ConfigurableEnvironment environment) {
    environment.getPropertySources().stream()
        .filter(o -> EnumerablePropertySource.class.isAssignableFrom(o.getClass()))
        .map(EnumerablePropertySource.class::cast)
        .flatMap(propertySource -> mapLegacyOverrideFromSource(environment, propertySource))
        .forEach(
            propertySource ->
                addMapPropertySourceFirst(
                    propertySource.sourceName(), propertySource.properties(), environment));
  }

  private Stream<MappedPropertySource> mapLegacyOverrideFromSource(
      final ConfigurableEnvironment environment, final EnumerablePropertySource<?> propertySource) {
    final Map<String, MappedPropertySource> result = new HashMap<>();
    for (final String propertyName : propertySource.getPropertyNames()) {
      for (final String prefix : LEGACY_OVERRIDE_PREFIX) {
        final String normalizedPropertyName = propertyName.replaceAll("_", ".").toLowerCase();
        if (normalizedPropertyName.startsWith(prefix)) {
          final String newPropertyName =
              OVERRIDE_PREFIX + normalizedPropertyName.substring(prefix.length());
          if (!environment.containsProperty(newPropertyName)) {
            final String sourceName = propertyName.replaceAll("\\[\\d*]", "");
            final MappedPropertySource mappedPropertySource =
                result.computeIfAbsent(
                    sourceName, s -> new MappedPropertySource(s, new HashMap<>()));
            mappedPropertySource
                .properties()
                .put(
                    newPropertyName,
                    Objects.requireNonNull(propertySource.getProperty(propertyName)));
            log.debug(
                String.format(
                    "Mapping worker override from '%s' to '%s'", propertyName, newPropertyName));
          }
        }
      }
    }
    return result.values().stream();
  }

  private void mapLegacyProperties(final ConfigurableEnvironment environment) {
    final List<CamundaClientLegacyPropertiesMapping> mappings =
        CamundaClientLegacyPropertiesMappingsLoader.load();
    mappings.stream()
        .map(mapping -> detectPropertyValue(environment, mapping))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .forEach(
            mappedPropertySource ->
                addMapPropertySourceFirst(
                    mappedPropertySource.sourceName(),
                    mappedPropertySource.properties(),
                    environment));
  }

  private Optional<MappedPropertySource> detectPropertyValue(
      final ConfigurableEnvironment environment,
      final CamundaClientLegacyPropertiesMapping property) {
    if (environment.containsProperty(property.getPropertyName())) {
      log.debug(
          String.format(
              "Property '%s' found, not looking up legacy properties", property.getPropertyName()));
      return Optional.empty();
    }
    for (final String legacyPropertyName : property.getLegacyPropertyNames()) {
      if (environment.containsProperty(legacyPropertyName)) {
        log.warn(
            String.format(
                "Legacy property '%s' found, setting to '%s'. Please update your setup to use the latest property",
                legacyPropertyName, property.getPropertyName()));
        return Optional.of(
            new MappedPropertySource(
                legacyPropertyName,
                Map.of(
                    property.getPropertyName(),
                    Objects.requireNonNull(environment.getProperty(legacyPropertyName)))));
      }
      // check for indexed property
      final Map<String, Object> indexedProperty = new HashMap<>();
      for (int i = 0; i < Integer.MAX_VALUE; i++) {
        final String indexedPropertyName = property.getPropertyName() + "[" + i + "]";
        final String indexedLegacyPropertyName = legacyPropertyName + "[" + i + "]";

        if (environment.containsProperty(indexedPropertyName)) {
          // the new property is already present, no need to override
          return Optional.empty();
        }
        if (!environment.containsProperty(indexedLegacyPropertyName)) {
          // the index is not present, the for-loop is interrupted
          break;
        }
        // the property is present, will be mapped
        indexedProperty.put(
            indexedPropertyName, environment.getProperty(indexedLegacyPropertyName));
      }
      if (!indexedProperty.isEmpty()) {
        log.warn(
            String.format(
                "Legacy property '%s' found, setting to '%s'. Please update your setup to use the latest property",
                legacyPropertyName, property.getPropertyName()));
        return Optional.of(new MappedPropertySource(legacyPropertyName, indexedProperty));
      }
    }
    log.debug(String.format("No property found for '%s'", property.getPropertyName()));
    return Optional.empty();
  }

  private void processClientMode(final ConfigurableEnvironment environment) {
    try {
      ClientMode clientMode = environment.getProperty(CAMUNDA_CLIENT_MODE, ClientMode.class);
      if (clientMode == null) {
        if (isImplicitSaas(environment)) {
          clientMode = ClientMode.saas;
        } else {
          return;
        }
      }

      final String propertiesFile = determinePropertiesFile(clientMode);
      addYamlPropertySourceLast(propertiesFile, environment);
    } catch (final Exception e) {
      throw new IllegalStateException("Error while post processing camunda properties", e);
    }
  }

  private void addMapPropertySourceFirst(
      final String sourceName,
      final Map<String, Object> properties,
      final ConfigurableEnvironment environment) {
    final PropertySource<?> propertySource = new MapPropertySource(sourceName, properties);
    environment.getPropertySources().addFirst(propertySource);
  }

  private void addYamlPropertySourceLast(
      final String propertiesFile, final ConfigurableEnvironment environment) throws IOException {
    final YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
    final ClassPathResource resource = new ClassPathResource(propertiesFile);
    final List<PropertySource<?>> props = loader.load(propertiesFile, resource);
    for (final PropertySource<?> prop : props) {
      environment.getPropertySources().addLast(prop); // lowest priority
    }
  }

  private boolean isImplicitSaas(final ConfigurableEnvironment environment) {
    if (environment.containsProperty("camunda.client.cloud.cluster-id")) {
      log.info(
          String.format(
              "Implicit '%s' '%s' detected, will be used", CAMUNDA_CLIENT_MODE, ClientMode.saas));
      return true;
    }
    return false;
  }

  private String determinePropertiesFile(final ClientMode clientMode) {
    return switch (clientMode) {
      case selfManaged -> "modes/self-managed.yaml";
      case saas -> "modes/saas.yaml";
    };
  }

  private record MappedPropertySource(String sourceName, Map<String, Object> properties) {}
}

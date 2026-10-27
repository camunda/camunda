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

import io.camunda.client.spring.properties.CamundaClientAuthProperties.AuthMethod;
import io.camunda.client.spring.properties.CamundaClientProperties.ClientMode;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.logging.Log;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.logging.DeferredLogFactory;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/**
 * Spring Boot 3 compatible version of this post processor. Implements the deprecated {@code
 * org.springframework.boot.env.EnvironmentPostProcessor}, since {@code
 * org.springframework.boot.EnvironmentPostProcessor} (used by the base module for Spring Boot 4.x)
 * does not exist in Spring Boot 3.x.
 */
public class CamundaClientPropertiesPostProcessor implements EnvironmentPostProcessor {

  public static final String CAMUNDA_CLIENT_AUTH_METHOD = "camunda.client.auth.method";
  public static final String CAMUNDA_CLIENT_MODE = "camunda.client.mode";
  private static final String OVERRIDE_PREFIX = "camunda.client.worker.override.";
  private static final List<String> LEGACY_OVERRIDE_PREFIX =
      List.of("camunda.client.zeebe.override.", "zeebe.client.worker.override.");
  private static final String CLUSTER_VARIABLES_ENABLED =
      "camunda.client.cluster-variables.enabled";
  private static final String CLUSTER_VARIABLES_GLOBAL = "camunda.client.cluster-variables.global";
  private static final String CLUSTER_VARIABLES_TENANT = "camunda.client.cluster-variables.tenant";
  private static final String CLUSTER_VARIABLES_VARIABLES =
      "camunda.client.cluster-variables.variables";
  private static final String CLUSTER_VARIABLES_LEGACY_SOURCE = "cluster-variables-legacy";
  private static final Map<AuthMethod, Set<String>> IMPLICIT_AUTH_METHOD_INDICATORS;

  static {
    IMPLICIT_AUTH_METHOD_INDICATORS = new HashMap<>();
    IMPLICIT_AUTH_METHOD_INDICATORS.put(
        AuthMethod.basic, Set.of("camunda.client.auth.username", "camunda.client.auth.password"));
    IMPLICIT_AUTH_METHOD_INDICATORS.put(
        AuthMethod.oidc,
        Set.of("camunda.client.auth.client-id", "camunda.client.auth.client-secret"));
  }

  private final Log log;

  public CamundaClientPropertiesPostProcessor(final DeferredLogFactory deferredLogFactory) {
    log = deferredLogFactory.getLog(getClass());
  }

  @Override
  public void postProcessEnvironment(
      final ConfigurableEnvironment environment, final SpringApplication application) {
    mapLegacyProperties(environment);
    mapLegacyOverrides(environment);
    mapLegacyClusterVariables(environment);
    processClientMode(environment);
    processAuthMethod(environment);
    // Note: a single-client application (only camunda.client.*) is projected onto one 'default'
    // client by MultiCamundaClientPropertiesResolver, which resolves through the Binder so relaxed
    // and environment-variable configuration is handled correctly.
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

  private void mapLegacyClusterVariables(final ConfigurableEnvironment environment) {
    if (environment.getPropertySources().contains(CLUSTER_VARIABLES_LEGACY_SOURCE)) {
      // Spring Cloud bootstrap runs EnvironmentPostProcessors twice; the legacy source added on
      // the first pass would otherwise be mistaken for a user-configured 'variables' property.
      return;
    }
    final Binder binder = Binder.get(environment);
    if (!binder.bind(CLUSTER_VARIABLES_ENABLED, Boolean.class).orElse(true)) {
      return;
    }
    final Map<String, Object> global =
        binder
            .bind(CLUSTER_VARIABLES_GLOBAL, Bindable.mapOf(String.class, Object.class))
            .orElseGet(Map::of);
    final Map<String, Object> tenant =
        binder
            .bind(CLUSTER_VARIABLES_TENANT, Bindable.mapOf(String.class, Object.class))
            .orElseGet(Map::of);
    if (global.isEmpty() && tenant.isEmpty()) {
      return;
    }

    final boolean hasVariables =
        !binder
            .bind(CLUSTER_VARIABLES_VARIABLES, Bindable.listOf(ClusterVariableEntry.class))
            .orElseGet(List::of)
            .isEmpty();
    if (hasVariables) {
      log.warn(
          String.format(
              "Ignoring '%s' and '%s' because '%s' is configured",
              CLUSTER_VARIABLES_GLOBAL, CLUSTER_VARIABLES_TENANT, CLUSTER_VARIABLES_VARIABLES));
      return;
    }

    final Map<String, Object> mapped = new LinkedHashMap<>();
    final int[] index = {0};
    if (!global.isEmpty()) {
      log.warn(
          String.format(
              "The property '%s' is deprecated, use '%s' instead",
              CLUSTER_VARIABLES_GLOBAL, CLUSTER_VARIABLES_VARIABLES));
      global.forEach((name, value) -> putClusterVariable(mapped, index[0]++, name, value, null));
    }
    if (!tenant.isEmpty()) {
      log.warn(
          String.format(
              "The property '%s' is deprecated, use '%s' instead",
              CLUSTER_VARIABLES_TENANT, CLUSTER_VARIABLES_VARIABLES));
      tenant.forEach(
          (tenantId, tenantVariables) -> {
            if (tenantId == null || tenantId.isBlank()) {
              throw new IllegalArgumentException(
                  "Invalid tenant ID in '"
                      + CLUSTER_VARIABLES_TENANT
                      + "': tenant ID must not be null or blank");
            }
            if (!(tenantVariables instanceof Map<?, ?>)) {
              throw new IllegalArgumentException(
                  "Invalid value for tenant '"
                      + tenantId
                      + "' in '"
                      + CLUSTER_VARIABLES_TENANT
                      + "': expected a map of variable name to value");
            }
            ((Map<?, ?>) tenantVariables)
                .forEach(
                    (name, value) ->
                        putClusterVariable(
                            mapped, index[0]++, String.valueOf(name), value, tenantId));
          });
    }
    addMapPropertySourceFirst(CLUSTER_VARIABLES_LEGACY_SOURCE, mapped, environment);
  }

  private static void putClusterVariable(
      final Map<String, Object> mapped,
      final int index,
      final String name,
      final Object value,
      final String tenantId) {
    mapped.put(CLUSTER_VARIABLES_VARIABLES + "[" + index + "].name", name);
    mapped.put(CLUSTER_VARIABLES_VARIABLES + "[" + index + "].value", value);
    if (tenantId != null) {
      mapped.put(CLUSTER_VARIABLES_VARIABLES + "[" + index + "].tenant-id", tenantId);
    }
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

  private void processAuthMethod(final ConfigurableEnvironment environment) {
    try {

      final ClientMode clientMode = environment.getProperty(CAMUNDA_CLIENT_MODE, ClientMode.class);
      AuthMethod authMethod = environment.getProperty(CAMUNDA_CLIENT_AUTH_METHOD, AuthMethod.class);
      if (clientMode == ClientMode.saas) {
        if (authMethod != AuthMethod.oidc) {
          // saas is set, but another auth method than oidc is set
          log.warn(
              String.format(
                  "'%s' is '%s', but '%s' is manually set to '%s', will be ignored and the application will fall back to use '%s'",
                  CAMUNDA_CLIENT_MODE,
                  clientMode,
                  CAMUNDA_CLIENT_AUTH_METHOD,
                  authMethod,
                  AuthMethod.oidc));
          addMapPropertySourceFirst(
              CAMUNDA_CLIENT_MODE,
              Map.of(CAMUNDA_CLIENT_AUTH_METHOD, AuthMethod.oidc),
              environment);
        }
        return;
      }
      if (authMethod == null) {
        final Map<AuthMethod, Set<String>> implicitAuthMethods =
            detectImplicitAuthMethods(environment);
        if (implicitAuthMethods.size() > 1) {
          throw new IllegalStateException(formatImplicitAuthModeIndicator(implicitAuthMethods));
        }
        if (implicitAuthMethods.size() == 1) {
          authMethod = implicitAuthMethods.keySet().stream().findFirst().get();
          log.info(
              String.format(
                  "Implicit '%s'='%s' detected due to '%s' being set.",
                  CAMUNDA_CLIENT_AUTH_METHOD,
                  authMethod,
                  implicitAuthMethods.entrySet().stream().findFirst().get().getValue()));
        }
      }
      if (authMethod == null) {
        log.warn(
            String.format(
                "No '%s' detected, will be set to '%s'",
                CAMUNDA_CLIENT_AUTH_METHOD, AuthMethod.none));
        authMethod = AuthMethod.none;
      }
      final String propertiesFile = determinePropertiesFile(authMethod);
      addYamlPropertySourceLast(propertiesFile, environment);
    } catch (final Exception e) {
      throw new IllegalStateException("Error while post processing camunda properties", e);
    }
  }

  static String formatImplicitAuthModeIndicator(
      final Map<AuthMethod, Set<String>> implicitAuthMethods) {
    return String.format(
        "Mutually exclusive implicit auth method indicators detected (%s)",
        implicitAuthMethods.entrySet().stream()
            .map(e -> "'" + e.getKey().name() + "' -> '" + String.join("', '", e.getValue()) + "'")
            .collect(Collectors.joining(",")));
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

  private Map<AuthMethod, Set<String>> detectImplicitAuthMethods(
      final ConfigurableEnvironment environment) {
    return IMPLICIT_AUTH_METHOD_INDICATORS.entrySet().stream()
        .map(
            e ->
                Map.entry(
                    e.getKey(),
                    e.getValue().stream()
                        .filter(environment::containsProperty)
                        .collect(Collectors.toSet())))
        .filter(e -> !e.getValue().isEmpty())
        .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
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

  private String determinePropertiesFile(final AuthMethod authMethod) {
    return switch (authMethod) {
      case basic -> "auth-methods/basic.yaml";
      case oidc -> "auth-methods/oidc.yaml";
      case none -> "auth-methods/none.yaml";
    };
  }

  private String determinePropertiesFile(final ClientMode clientMode) {
    return switch (clientMode) {
      case selfManaged -> "modes/self-managed.yaml";
      case saas -> "modes/saas.yaml";
    };
  }

  private record MappedPropertySource(String sourceName, Map<String, Object> properties) {}
}

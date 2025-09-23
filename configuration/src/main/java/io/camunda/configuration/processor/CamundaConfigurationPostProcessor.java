/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.processor;

import io.camunda.configuration.UnifiedConfigurationException;
import io.camunda.configuration.processor.CamundaLegacyPropertiesMapping.LegacyProperty;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.logging.DeferredLog;
import org.springframework.boot.logging.DeferredLogFactory;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.PropertySource;

public class CamundaConfigurationPostProcessor implements EnvironmentPostProcessor {
  private static final Map<String, Function<Object, Object>> MAPPER_FUNCTIONS =
      Map.of("secondsToDuration", s -> Duration.ofSeconds(Long.parseLong((String) s)));
  private final DeferredLog log;

  public CamundaConfigurationPostProcessor(final DeferredLogFactory deferredLogFactory) {
    log = (DeferredLog) deferredLogFactory.getLog(getClass());
  }

  @Override
  public void postProcessEnvironment(
      final ConfigurableEnvironment environment, final SpringApplication application) {
    mapLegacyProperties(environment);
  }

  private void mapLegacyProperties(final ConfigurableEnvironment environment) {
    final Set<String> profiles =
        Arrays.stream(environment.getActiveProfiles()).collect(Collectors.toSet());
    log.debug(String.format("Detected profiles: %s", profiles));
    final List<CamundaLegacyPropertiesMapping> mappings =
        CamundaLegacyPropertiesMappingLoader.load();
    mappings.stream()
        .flatMap(mapping -> detectPropertyValue(environment, mapping, profiles))
        .forEach(
            mappedPropertySource ->
                addMapPropertySourceFirst(
                    mappedPropertySource.sourceName(),
                    mappedPropertySource.properties(),
                    environment));
  }

  private void addMapPropertySourceFirst(
      final String sourceName,
      final Map<String, Object> properties,
      final ConfigurableEnvironment environment) {
    log.debug(
        String.format(
            "Mapping property '%s' to '%s'", sourceName, String.join("', '", properties.keySet())));
    final Properties prop = new Properties();
    prop.putAll(properties);
    final PropertySource<?> propertySource = new PropertiesPropertySource(sourceName, prop);
    environment.getPropertySources().addFirst(propertySource);
  }

  private Function<Object, Object> findMapper(final String mapperName) {
    if (mapperName == null) {
      return o -> o;
    }
    if (MAPPER_FUNCTIONS.containsKey(mapperName)) {
      return MAPPER_FUNCTIONS.get(mapperName);
    }
    throw new UnifiedConfigurationException("Unknown mapper: " + mapperName);
  }

  private Stream<MappedPropertySource> detectPropertyValue(
      final ConfigurableEnvironment environment,
      final CamundaLegacyPropertiesMapping property,
      final Set<String> profiles) {
    if (property.newProperty().endsWith("*")) {
      return detectWildcardProperties(environment, property, profiles);
    }
    if (environment.containsProperty(property.newProperty())) {
      log.debug(
          String.format(
              "Property '%s' found, not looking up legacy properties", property.newProperty()));
      return Stream.of();
    }
    final Set<Set<LegacyProperty>> appliedLegacyProperties =
        getAppliedLegacyProperties(property, profiles);
    if (property.newProperty().endsWith("*")) {
      return detectWildcardProperties(environment, property, profiles);
    }
    for (final Set<LegacyProperty> validPropertyNames : appliedLegacyProperties) {
      if (!haveMatchingValues(
          validPropertyNames.stream().map(LegacyProperty::name).collect(Collectors.toSet()),
          environment)) {
        throw new UnifiedConfigurationException(
            "The legacy properties " + validPropertyNames + " do not have matching values");
      }
      final Set<LegacyProperty> legacyPropertiesWithValue =
          findPropertiesWithValue(validPropertyNames, environment);
      if (!legacyPropertiesWithValue.isEmpty()) {
        log.warn(
            String.format(
                "Legacy properties %s found, setting to '%s'. Please update your setup to use the latest property",
                legacyPropertiesWithValue.stream().map(LegacyProperty::name).toList(),
                property.newProperty()));
        return legacyPropertiesWithValue.stream()
            .map(
                legacyProperty ->
                    new MappedPropertySource(
                        legacyProperty.name(),
                        Map.of(
                            property.newProperty(),
                            findMapper(legacyProperty.mapper())
                                .apply(
                                    Objects.requireNonNull(
                                        environment.getProperty(legacyProperty.name()))))));
      }
    }
    log.debug(String.format("No property found for '%s'", property.newProperty()));
    return Stream.of();
  }

  private Stream<MappedPropertySource> detectWildcardProperties(
      final ConfigurableEnvironment environment,
      final CamundaLegacyPropertiesMapping property,
      final Set<String> profiles) {
    log.debug(String.format("Processing Wildcard properties for '%s'", property.newProperty()));
    final Map<String, String> normalizedPropertyNames =
        environment.getPropertySources().stream()
            .filter(propertySource -> propertySource instanceof EnumerablePropertySource<?>)
            .map(EnumerablePropertySource.class::cast)
            .flatMap(propertySource -> Stream.of(propertySource.getPropertyNames()))
            .map(propertyName -> Map.entry(normalize(propertyName, true), propertyName))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (k1, k2) -> k1));
    final Set<Set<LegacyProperty>> appliedLegacyProperties =
        getAppliedLegacyProperties(property, profiles);
    for (final Set<LegacyProperty> containedProperties : appliedLegacyProperties) {
      return containedProperties.stream()
          .filter(
              p ->
                  normalizedPropertyNames.keySet().stream()
                      .anyMatch(npn -> npn.startsWith(normalize(composePrefix(p.name()), false))))
          .map(
              p ->
                  new MappedPropertySource(
                      p.name(),
                      normalizedPropertyNames.keySet().stream()
                          .filter(np -> np.startsWith(normalize(composePrefix(p.name()), false)))
                          .filter(
                              np ->
                                  !environment.containsProperty(
                                      normalize(
                                          composeNewPropertyName(p, np, property.newProperty()),
                                          true)))
                          .filter(
                              np ->
                                  !normalizedPropertyNames.containsKey(
                                      normalize(
                                          composeNewPropertyName(p, np, property.newProperty()),
                                          true)))
                          .map(
                              np ->
                                  Map.entry(
                                      normalize(
                                          composeNewPropertyName(p, np, property.newProperty()),
                                          true),
                                      findMapper(p.mapper())
                                          .apply(
                                              environment.getProperty(
                                                  normalizedPropertyNames.get(np)))))
                          .collect(Collectors.toMap(Entry::getKey, Entry::getValue))));
    }
    return Stream.of();
  }

  private String composePrefix(final String propertyNameWithWildcard) {
    return propertyNameWithWildcard.replaceAll("\\.\\*", "");
  }

  private String composeNewPropertyName(
      final LegacyProperty legacyProperty,
      final String normalizedPropertyName,
      final String newPropertyName) {
    final String newPropertyNameWithoutWildcard = normalize(composePrefix(newPropertyName), false);
    final String legacyPropertyNameWithoutWildcard =
        normalize(composePrefix(legacyProperty.name()), false);
    final String newComposedPropertyName =
        normalizedPropertyName.replace(
            legacyPropertyNameWithoutWildcard, newPropertyNameWithoutWildcard);
    log.debug(
        String.format(
            "Transforming legacy property '%s' to '%s'",
            normalizedPropertyName, newComposedPropertyName));
    return newComposedPropertyName;
  }

  private Set<Set<LegacyProperty>> getAppliedLegacyProperties(
      final CamundaLegacyPropertiesMapping property, final Set<String> profiles) {
    return property.legacyProperties().stream()
        .map(
            legacyProperties ->
                legacyProperties.stream()
                    .filter(
                        legacyProperty ->
                            legacyProperty.profiles() == null
                                || legacyProperty.profiles().stream()
                                    .filter(p -> !p.startsWith("!"))
                                    .anyMatch(profiles::contains))
                    .filter(
                        legacyProperty ->
                            legacyProperty.profiles() == null
                                || legacyProperty.profiles().stream()
                                    .filter(p -> p.startsWith("!"))
                                    .map(p -> p.substring(1))
                                    .noneMatch(profiles::contains))
                    .collect(Collectors.toSet()))
        .collect(Collectors.toSet());
  }

  private String normalize(final String property, final boolean preserveLastPropertyFormat) {
    if (preserveLastPropertyFormat) {
      final String[] split = property.split("\\.");
      final String last = split[split.length - 1];
      final String rest = String.join(".", Arrays.asList(split).subList(0, split.length - 1));
      return rest.replaceAll("_", ".").replaceAll("-", "").toLowerCase() + "." + last.toLowerCase();

    } else {
      return property.replaceAll("_", ".").replaceAll("-", "").toLowerCase();
    }
  }

  private Set<LegacyProperty> findPropertiesWithValue(
      final Set<LegacyProperty> propertyNames, final ConfigurableEnvironment environment) {
    return propertyNames.stream()
        .filter(legacyProperty -> environment.containsProperty(legacyProperty.name()))
        .collect(Collectors.toSet());
  }

  private boolean haveMatchingValues(
      final Set<String> propertyNames, final ConfigurableEnvironment environment) {
    return propertyNames.stream()
            .map(environment::getProperty)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet())
            .size()
        <= 1;
  }

  private record MappedPropertySource(String sourceName, Map<String, Object> properties) {}
}

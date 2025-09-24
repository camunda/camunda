/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.processor;

import io.camunda.configuration.UnifiedConfigurationException;
import io.camunda.configuration.processor.CamundaConfigurationPostProcessor.MappedPropertySource.Fail;
import io.camunda.configuration.processor.CamundaConfigurationPostProcessor.MappedPropertySource.Success;
import io.camunda.configuration.processor.CamundaLegacyPropertiesMapping.LegacyProperty;
import io.camunda.configuration.processor.CamundaLegacyPropertiesMapping.LegacyProperty.Mapper;
import io.camunda.configuration.processor.CamundaLegacyPropertiesMapping.Mode;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
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
    final Collection<MappedPropertySource> mappedPropertySources =
        mappings.stream()
            .flatMap(mapping -> detectPropertyValue(environment, mapping, profiles))
            .map(MappedPropertySource.class::cast)
            .collect(
                Collectors.toMap(
                    MappedPropertySource::sourceName,
                    m -> m,
                    (m1, m2) -> {
                      if (m1 instanceof Fail) {
                        return m2;
                      }
                      final Success m1Success = (Success) m1;
                      if (m2 instanceof Fail) {
                        return m1;
                      }
                      final Success m2Success = (Success) m2;
                      final Map<String, Object> combinedProperties = new HashMap<>();
                      combinedProperties.putAll(m1Success.properties());
                      combinedProperties.putAll(m2Success.properties());
                      return new Success(m1.sourceName(), combinedProperties);
                    }))
            .values();
    final Set<Fail> fails =
        mappedPropertySources.stream()
            .filter(Fail.class::isInstance)
            .map(Fail.class::cast)
            .collect(Collectors.toSet());
    if (!fails.isEmpty()) {
      throw new UnifiedConfigurationException(
          String.format(
              "The following issues have been detected:%s",
              fails.stream()
                  .map(Fail::errorMessage)
                  .map(errorMessage -> "\n   - " + errorMessage)
                  .collect(Collectors.joining())));
    }
    mappedPropertySources.stream()
        .filter(Success.class::isInstance)
        .map(Success.class::cast)
        .forEach(
            success ->
                addMapPropertySourceFirst(success.sourceName(), success.properties(), environment));
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

  private Function<Object, Object> findMapperFunction(final Mapper mapper) {
    if (mapper == null) {
      return o -> o;
    }
    return mapper.getMapper();
  }

  private Stream<? extends MappedPropertySource> detectPropertyValue(
      final ConfigurableEnvironment environment,
      final CamundaLegacyPropertiesMapping property,
      final Set<String> profiles) {
    final Mode mode = property.mode() != null ? property.mode() : Mode.supported;
    if (property.newProperty().endsWith("*")) {
      return detectWildcardProperties(environment, property, profiles);
    }
    final boolean newPropertyPresent = environment.containsProperty(property.newProperty());
    if (newPropertyPresent) {
      log.debug(
          String.format(
              "Property '%s' found, not looking up legacy properties", property.newProperty()));
      if (mode == Mode.supported) {
        return Stream.of();
      }
    }
    final Set<Set<LegacyProperty>> appliedLegacyProperties =
        getAppliedLegacyProperties(property, profiles);
    for (final Set<LegacyProperty> validPropertyNames : appliedLegacyProperties) {
      if (!haveMatchingValues(
          validPropertyNames.stream().map(LegacyProperty::name).collect(Collectors.toSet()),
          property.newProperty(),
          mode,
          environment)) {
        final Set<String> validPropertyNameSet =
            validPropertyNames.stream().map(LegacyProperty::name).collect(Collectors.toSet());
        final String errorMessage =
            mode == Mode.supportedOnlyIfValuesMatch
                ? String.format(
                    "Ambiguous configuration. The properties '%s' and the new property '%s' have conflicting values",
                    String.join(", ", validPropertyNameSet), property.newProperty())
                : String.format(
                    "Ambiguous configuration. The properties '%s' have conflicting values",
                    String.join(", ", validPropertyNameSet));
        return findPropertiesWithValue(validPropertyNames, environment).stream()
            .map(legacyProperty -> new Fail(legacyProperty.name(), errorMessage));
      }
      final Set<LegacyProperty> legacyPropertiesWithValue =
          findPropertiesWithValue(validPropertyNames, environment);
      if (!legacyPropertiesWithValue.isEmpty()) {
        if (mode == Mode.notSupported) {
          final String errorMessage =
              String.format(
                  "The following legacy configuration properties are no longer supported and must be removed in favor of '%s': %s",
                  property.newProperty(),
                  String.join(
                      ", ",
                      legacyPropertiesWithValue.stream()
                          .map(LegacyProperty::name)
                          .collect(Collectors.toSet())));
          return legacyPropertiesWithValue.stream()
              .map(legacyProperty -> new Fail(legacyProperty.name(), errorMessage));
        }
        log.warn(
            String.format(
                "The following legacy configuration properties should be removed in favor of '%s': %s",
                property.newProperty(),
                String.join(
                    ", ",
                    legacyPropertiesWithValue.stream()
                        .map(LegacyProperty::name)
                        .collect(Collectors.toSet()))));
        if (newPropertyPresent) {
          return Stream.of();
        }
        return legacyPropertiesWithValue.stream()
            .map(
                legacyProperty ->
                    new Success(
                        legacyProperty.name(),
                        Map.of(
                            property.newProperty(),
                            findMapperFunction(legacyProperty.mapper())
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
                  new Success(
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
                                      findMapperFunction(p.mapper())
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
      final Set<String> propertyNames,
      final String newProperty,
      final Mode mode,
      final ConfigurableEnvironment environment) {
    final Set<String> values =
        propertyNames.stream()
            .map(environment::getProperty)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    if (mode == Mode.supportedOnlyIfValuesMatch) {
      final String newValue = environment.getProperty(newProperty);
      values.add(newValue);
    }
    return values.size() <= 1;
  }

  sealed interface MappedPropertySource {
    String sourceName();

    record Fail(String sourceName, String errorMessage) implements MappedPropertySource {}

    record Success(String sourceName, Map<String, Object> properties)
        implements MappedPropertySource {}
  }
}

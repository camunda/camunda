/*
 * Copyright 2018-present Open Networking Foundation
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atomix.core.utils.config;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Joiner;
import com.typesafe.config.Config;
import io.atomix.core.AtomixRegistry;
import io.atomix.utils.config.ConfigMapper;
import io.atomix.utils.config.ConfigurationException;
import io.atomix.utils.config.TypedConfig;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

/** Polymorphic configuration mapper. */
public class PolymorphicConfigMapper extends ConfigMapper {
  private final AtomixRegistry registry;
  private final Collection<PolymorphicTypeMapper> polymorphicTypes;

  public PolymorphicConfigMapper(final ClassLoader classLoader, final AtomixRegistry registry) {
    this(classLoader, registry, Collections.emptyList());
  }

  public PolymorphicConfigMapper(
      final ClassLoader classLoader,
      final AtomixRegistry registry,
      final PolymorphicTypeMapper... mappers) {
    this(classLoader, registry, Arrays.asList(mappers));
  }

  public PolymorphicConfigMapper(
      final ClassLoader classLoader,
      final AtomixRegistry registry,
      final Collection<PolymorphicTypeMapper> mappers) {
    super(classLoader);
    this.registry = checkNotNull(registry);
    this.polymorphicTypes = mappers;
  }

  @Override
  @SuppressWarnings("unchecked")
  protected <T> T newInstance(final Config config, final String key, final Class<T> clazz) {
    final T instance;

    // If the class is a polymorphic type, look up the type mapper and get the concrete type.
    if (isPolymorphicType(clazz)) {
      final PolymorphicTypeMapper typeMapper =
          polymorphicTypes.stream()
              .filter(mapper -> mapper.getConfigClass().isAssignableFrom(clazz))
              .filter(
                  mapper ->
                      (mapper.getTypePath() != null && config.hasPath(mapper.getTypePath()))
                          || mapper.getTypePath() == null)
              .findFirst()
              .orElse(null);
      if (typeMapper == null) {
        throw new ConfigurationException("Cannot instantiate abstract type " + clazz.getName());
      }

      final String typeName =
          typeMapper.getTypePath() != null ? config.getString(typeMapper.getTypePath()) : key;
      final Class<? extends TypedConfig<?>> concreteClass =
          typeMapper.getConcreteClass(registry, typeName);
      if (concreteClass == null) {
        throw new ConfigurationException("Unknown " + key + " type '" + typeName + "'");
      }
      try {
        instance = (T) concreteClass.newInstance();
      } catch (final InstantiationException | IllegalAccessException e) {
        throw new ConfigurationException(
            concreteClass.getName() + " needs a public no-args constructor to be used as a bean",
            e);
      }
    } else {
      try {
        instance = clazz.newInstance();
      } catch (final InstantiationException | IllegalAccessException e) {
        throw new ConfigurationException(
            clazz.getName() + " needs a public no-args constructor to be used as a bean", e);
      }
    }
    return instance;
  }

  @Override
  protected void checkRemainingProperties(
      final Set<String> missingProperties,
      final List<String> availableProperties,
      final String path,
      final Class<?> clazz) {
    final Properties properties = new Properties();
    properties.putAll(System.getProperties());

    final List<String> cleanNames =
        missingProperties.stream()
            .filter(
                propertyName ->
                    !isPolymorphicType(clazz)
                        || !polymorphicTypes.stream()
                            .anyMatch(type -> Objects.equals(type.getTypePath(), propertyName)))
            .map(propertyName -> toPath(path, propertyName))
            .filter(propertyName -> !properties.containsKey(propertyName))
            .filter(
                propertyName ->
                    properties.entrySet().stream()
                        .noneMatch(
                            entry -> entry.getKey().toString().startsWith(propertyName + ".")))
            .sorted()
            .collect(Collectors.toList());
    if (!cleanNames.isEmpty()) {
      throw new ConfigurationException(
          "Unknown properties present in configuration: "
              + Joiner.on(", ").join(cleanNames)
              + "\n"
              + "Available properties:\n- "
              + Joiner.on("\n- ").join(availableProperties));
    }
  }

  /** Returns a boolean indicating whether the given class is a polymorphic type. */
  private boolean isPolymorphicType(final Class<?> clazz) {
    return polymorphicTypes.stream()
        .anyMatch(polymorphicType -> polymorphicType.getConfigClass() == clazz);
  }
}

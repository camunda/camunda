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
package io.camunda.zeebe.spring.client.configuration;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PropertyUtil {
  private static final Logger LOG = LoggerFactory.getLogger(PropertyUtil.class);

  /**
   * Returns the property in the given relevance: legacyProperty, property,defaultProperty
   *
   * @param propertyName the name of the property, used for logging
   * @param propertySupplier a function to supply the property, may throw
   * @param legacyPropertySupplier a function to supply the legacy property, may throw
   * @param defaultProperty the default to apply if nothing else suits, may be null
   * @param configCache the cache to save the property to, may be null
   * @return the property resolved
   * @param <T> the type of the property
   */
  public static <T> T getOrLegacyOrDefault(
      final String propertyName,
      final Supplier<T> propertySupplier,
      final Supplier<T> legacyPropertySupplier,
      final T defaultProperty,
      final Map<String, Object> configCache) {

    if (configCache != null && configCache.containsKey(propertyName)) {
      final Object propertyValue = configCache.get(propertyName);
      LOG.debug(
          "Property {} loading from cache. Property is set to {}", propertyName, propertyValue);
      return (T) propertyValue;
    }
    T property = getPropertyFromSupplier(legacyPropertySupplier, propertyName, "legacy");
    if (property == null || property.equals(defaultProperty)) {
      property = getPropertyFromSupplier(propertySupplier, propertyName, "property");
    }
    if (property == null || property.equals(defaultProperty)) {
      LOG.debug("Property {}: not set or default, using default", propertyName);
      property = defaultProperty;
    }
    if (configCache != null) {
      configCache.put(propertyName, property);
    }

    LOG.debug("Property {} set to {}", propertyName, property);
    return property;
  }

  /**
   * Returns the property in the given relevance: property, defaultProperty
   *
   * @param propertyName the name of the property, used for logging
   * @param propertySupplier a function to supply the property, may throw
   * @param defaultProperty the default to apply if nothing else suits, may be null
   * @param configCache the cache to save the property to, may be null
   * @return the property resolved
   * @param <T> the type of the property
   */
  public static <T> T getOrDefault(
      final String propertyName,
      final Supplier<T> propertySupplier,
      final T defaultProperty,
      final Map<String, Object> configCache) {
    return getOrLegacyOrDefault(
        propertyName, propertySupplier, noPropertySupplier(), defaultProperty, configCache);
  }

  private static <T> Supplier<T> noPropertySupplier() {
    return () -> null;
  }

  private static <T> T getPropertyFromSupplier(
      final Supplier<T> supplier, final String propertyName, final String propertyType) {
    T property = null;
    try {
      LOG.debug(
          "Property {}: not set or default, applying {} property", propertyName, propertyType);
      property = supplier.get();
    } catch (final Exception e) {
      LOG.debug("Error while loading {} property {}", propertyType, propertyName, e);
    }
    return property;
  }

  public static <T> Supplier<T> prioritized(
      final T defaultProperty, final List<Supplier<T>> suppliers) {
    for (final Supplier<T> supplier : suppliers) {
      try {
        final T property = supplier.get();
        if (property != null && !property.equals(defaultProperty)) {
          return supplier;
        }
      } catch (final Exception e) {
        // ignore
      }
    }
    return () -> defaultProperty;
  }
}

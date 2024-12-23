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
package io.camunda.spring.client.configuration;

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
   * @param propertySuppliers a list of functions to supply the property, may throw, first has the
   *     highest priority
   * @param defaultProperty the default to apply if nothing else suits, may be null
   * @param configCache the cache to save the property to, may be null
   * @return the property resolved
   * @param <T> the type of the property
   */
  @SafeVarargs
  public static <T> T getProperty(
      final String propertyName,
      final Map<String, Object> configCache,
      final T defaultProperty,
      final Supplier<T>... propertySuppliers) {

    if (configCache != null && configCache.containsKey(propertyName)) {
      final Object propertyValue = configCache.get(propertyName);
      LOG.debug(
          "Property {} loading from cache. Property is set to {}", propertyName, propertyValue);
      return (T) propertyValue;
    }
    T property = null;
    for (final Supplier<T> supplier : propertySuppliers) {
      if (property == null || property.equals(defaultProperty)) {
        property = getPropertyFromSupplier(supplier, propertyName);
      }
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

  private static <T> Supplier<T> noPropertySupplier() {
    return () -> null;
  }

  private static <T> T getPropertyFromSupplier(
      final Supplier<T> supplier, final String propertyName) {
    T property = null;
    try {
      LOG.debug("Property {}: not set or default, applying next property", propertyName);
      property = supplier.get();
    } catch (final Exception e) {
      LOG.debug("Error while loading next property {}", propertyName, e);
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

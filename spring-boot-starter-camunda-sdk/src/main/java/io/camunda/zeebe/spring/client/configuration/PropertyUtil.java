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
      String propertyName,
      Supplier<T> propertySupplier,
      Supplier<T> legacyPropertySupplier,
      T defaultProperty,
      Map<String, Object> configCache) {
    T property = defaultProperty;
    if (configCache != null) {
      try {
        LOG.debug("Property {}: Loading from cache", propertyName);
        if (configCache.containsKey(propertyName)) {
          return (T) configCache.get(propertyName);
        }
      } catch (Exception e) {
        LOG.debug("Error while loading cached property " + propertyName, e);
      }
    }
    if (property == null || property.equals(defaultProperty)) {
      LOG.debug("Propery {}: not set or default, applying legacy property", propertyName);
      try {
        property = legacyPropertySupplier.get();
      } catch (Exception e) {
        LOG.debug("Error while loading legacy property " + propertyName, e);
      }
    }
    if (property == null || property.equals(defaultProperty)) {
      LOG.debug("Propery {}: not set or default, applying property", propertyName);
      try {
        property = propertySupplier.get();
      } catch (Exception e) {
        LOG.debug("Error while loading property " + propertyName, e);
      }
    }
    if (property == null || property.equals(defaultProperty)) {
      LOG.debug("Propery {}: not set or default, using default", propertyName);
      property = defaultProperty;
    }
    if (configCache != null) {
      configCache.put(propertyName, property);
    }
    return property;
  }

  public static <T> Supplier<T> prioritized(T defaultProperty, List<Supplier<T>> suppliers) {
    for (Supplier<T> supplier : suppliers) {
      try {
        T property = supplier.get();
        if (property != null && !property.equals(defaultProperty)) {
          return supplier;
        }
      } catch (Exception e) {
        // ignore
      }
    }
    return () -> defaultProperty;
  }
}

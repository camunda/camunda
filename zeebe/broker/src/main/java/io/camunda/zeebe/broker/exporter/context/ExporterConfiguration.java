/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.exporter.context;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import io.camunda.zeebe.exporter.api.context.Configuration;
import io.camunda.zeebe.util.ReflectUtil;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public record ExporterConfiguration(String id, Map<String, Object> arguments)
    implements Configuration {

  // Accepts more lenient cases, such that the property "something" would match a field "someThing"
  // Note however that if a field "something" and "someThing" are present, only one of them will be
  // instantiated (the last declared one), using the last matching value.
  private static final ObjectMapper MAPPER =
      JsonMapper.builder()
          .addModule(
              new SimpleModule()
                  .addDeserializer(List.class, new ExporterConfigurationListDeserializer<>()))
          .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
          .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
          .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_VALUES)
          .enable(JsonReadFeature.ALLOW_SINGLE_QUOTES)
          .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
          .build();

  private static final Logger LOG = LoggerFactory.getLogger(ExporterConfiguration.class);

  @Override
  public String getId() {
    return id;
  }

  @Override
  public Map<String, Object> getArguments() {
    return arguments;
  }

  @Override
  public <T> T instantiate(final Class<T> configClass) {
    if (arguments != null) {
      try {
        return MAPPER.convertValue(arguments, configClass);
      } catch (final IllegalArgumentException e) {
        // Kept in line with main, where STRICT_MAPPER (@StrictConfiguration) rejects unknown
        // properties. MAPPER here ignores them, so this is unreachable until that mapper is
        // backported. Must stay before the MismatchedInputException check, its superclass, which
        // would otherwise swallow the property name and known-properties hint.
        if (e.getCause() instanceof final UnrecognizedPropertyException upe) {
          // getReferringClass() is the actual class that owns the unrecognized property.
          // When the typo is inside a nested object it differs from configClass, so prefer it;
          // fall back to configClass only if Jackson did not populate it.
          final var referringClass = upe.getReferringClass();
          final var className =
              referringClass != null ? referringClass.getSimpleName() : configClass.getSimpleName();
          // getPathReference() produces a human-readable path like
          // "ElasticsearchExporterConfiguration[\"index\"]->IndexConfiguration[\"unknownProp\"]"
          // which lets users locate the typo even in deeply nested configs.
          final var message =
              "Unknown exporter configuration property '"
                  + upe.getPropertyName()
                  + "' in "
                  + className
                  + " (path: "
                  + upe.getPathReference()
                  + "); known properties: "
                  + upe.getKnownPropertyIds();
          LOG.error(message);
          throw new IllegalArgumentException(message, upe);
        }
        if (e.getCause() instanceof final MismatchedInputException mie) {
          // The Jackson message quotes the offending value, which may be a credential. Report
          // only the path and do not chain the cause, as its message has the value.
          throw new IllegalArgumentException(
              "Invalid exporter configuration value in "
                  + configClass.getSimpleName()
                  + " (path: "
                  + mie.getPathReference()
                  + ")");
        }
        throw e;
      }
    } else {
      return ReflectUtil.newInstance(configClass);
    }
  }
}

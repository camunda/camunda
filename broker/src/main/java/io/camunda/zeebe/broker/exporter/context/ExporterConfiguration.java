/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.exporter.context;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.camunda.zeebe.exporter.api.context.Configuration;
import io.camunda.zeebe.util.ReflectUtil;
import java.util.Map;

public record ExporterConfiguration(String id, Map<String, Object> arguments)
    implements Configuration {

  // Accepts more lenient cases, such that the property "something" would match a field "someThing"
  // Note however that if a field "something" and "someThing" are present, only one of them will be
  // instantiated (the last declared one), using the last matching value.
  private static final ObjectMapper MAPPER =
      JsonMapper.builder()
          .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
          .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
          .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_VALUES)
          .enable(JsonReadFeature.ALLOW_SINGLE_QUOTES)
          .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
          .build();

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
      return MAPPER.convertValue(arguments, configClass);
    } else {
      return ReflectUtil.newInstance(configClass);
    }
  }
}

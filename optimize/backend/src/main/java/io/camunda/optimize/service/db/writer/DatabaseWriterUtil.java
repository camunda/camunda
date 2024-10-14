/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.writer;

import static io.camunda.optimize.service.db.DatabaseConstants.OPTIMIZE_DATE_FORMAT;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.service.db.schema.ScriptData;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class DatabaseWriterUtil {

  public static final DateTimeFormatter dateTimeFormatter =
      DateTimeFormatter.ofPattern(OPTIMIZE_DATE_FORMAT);

  private DatabaseWriterUtil() {}

  public static <T> Map<String, T> createFieldUpdateScriptParams(
      final Set<String> fields, final Object entityDto, final ObjectMapper objectMapper) {
    final Map<String, Object> entityAsMap =
        objectMapper.convertValue(entityDto, new TypeReference<>() {});
    final Map<String, T> params = new HashMap<>();
    for (final String fieldName : fields) {
      Object fieldValue = entityAsMap.get(fieldName);
      if (fieldValue != null) {
        if (fieldValue instanceof final TemporalAccessor temporalAccessor) {
          fieldValue = dateTimeFormatter.format(temporalAccessor);
        }
        params.put(fieldName, (T) fieldValue);
      }
    }
    return params;
  }

  public static String createUpdateFieldsScript(final Set<String> fieldKeys) {
    return fieldKeys.stream()
        .map(fieldKey -> String.format("%s.%s = params.%s;%n", "ctx._source", fieldKey, fieldKey))
        .collect(Collectors.joining());
  }

  public static ScriptData createScriptData(
      final String stringScript,
      final Map<String, Object> params,
      final ObjectMapper objectMapper) {
    return new ScriptData(mapParamsForScriptCreation(params, objectMapper), stringScript);
  }

  public static ScriptData createScriptData(
      final Set<String> fields, final Object entityDto, final ObjectMapper objectMapper) {
    final Map<String, Object> params =
        createFieldUpdateScriptParams(fields, entityDto, objectMapper);
    return new ScriptData(
        mapParamsForScriptCreation(params, objectMapper),
        createUpdateFieldsScript(params.keySet()));
  }

  public static <T> Map<String, T> mapParamsForScriptCreation(
      final Map<String, T> parameters, final ObjectMapper objectMapper) {
    return Optional.ofNullable(parameters)
        // This conversion seems redundant but it's not. In case the values are specific dto objects
        // this ensures they
        // get converted to generic objects that the elasticsearch client is happy to serialize
        // while it complains on
        // specific DTO's
        .map(value -> objectMapper.convertValue(value, new TypeReference<Map<String, T>>() {}))
        .orElse(Collections.emptyMap());
  }
}

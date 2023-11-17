/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.writer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.db.DatabaseConstants.OPTIMIZE_DATE_FORMAT;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class DatabaseWriterUtil {

  public static final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(OPTIMIZE_DATE_FORMAT);

  public static <T> Map<String, T> createFieldUpdateScriptParams(final Set<String> fields,
                                                                  final Object entityDto,
                                                                  final ObjectMapper objectMapper) {
    Map<String, Object> entityAsMap =
      objectMapper.convertValue(entityDto, new TypeReference<>() {
      });
    final Map<String, T> params = new HashMap<>();
    for (String fieldName : fields) {
      Object fieldValue = entityAsMap.get(fieldName);
      if (fieldValue != null) {
        if (fieldValue instanceof TemporalAccessor temporalAccessor) {
          fieldValue = dateTimeFormatter.format(temporalAccessor);
        }
        params.put(fieldName, (T) fieldValue);
      }
    }
    return params;
  }

  public static String createUpdateFieldsScript(final Set<String> fieldKeys) {
    return fieldKeys
      .stream()
      .map(fieldKey -> String.format("%s.%s = params.%s;%n", "ctx._source", fieldKey, fieldKey))
      .collect(Collectors.joining());
  }

  public static <T> Map<String, T> mapParamsForScriptCreation(final Map<String, T> parameters,
                                                                final ObjectMapper objectMapper) {
    return Optional.ofNullable(parameters)
      // This conversion seems redundant but it's not. In case the values are specific dto objects this ensures they
      // get converted to generic objects that the elasticsearch client is happy to serialize while it complains on
      // specific DTO's
      .map(value -> objectMapper.convertValue(
        value,
        new TypeReference<Map<String, T>>() {
        }
      ))
      .orElse(Collections.emptyMap());
  }
}

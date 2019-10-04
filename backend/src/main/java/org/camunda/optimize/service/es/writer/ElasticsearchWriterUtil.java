/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.experimental.UtilityClass;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;

import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.OPTIMIZE_DATE_FORMAT;

@UtilityClass
public class ElasticsearchWriterUtil {
  private static DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(OPTIMIZE_DATE_FORMAT);

  static Script createFieldUpdateScript(final Set<String> fields,
                                        final Object entityDto,
                                        final ObjectMapper objectMapper) {
    Map<String, Object> entityAsMap =
      objectMapper.convertValue(entityDto, new TypeReference<Map<String, Object>>() {
      });
    final Map<String, Object> params = new HashMap<>();
    for (String fieldName : fields) {
      Object fieldValue = entityAsMap.get(fieldName);
      if (fieldValue != null) {
        if (fieldValue instanceof TemporalAccessor) {
          fieldValue = dateTimeFormatter.format((TemporalAccessor) fieldValue);
        }
        params.put(fieldName, fieldValue);
      }
    }

    return createDefaultScript(ElasticsearchWriterUtil.createUpdateFieldsScript(params.keySet()), params);
  }

  public static Script createDefaultScript(final String inlineUpdateScript, final Map<String, Object> params) {
    return new Script(
      ScriptType.INLINE,
      Script.DEFAULT_SCRIPT_LANG,
      inlineUpdateScript,
      params
    );
  }

  static String createUpdateFieldsScript(final Set<String> fieldKeys) {
    return createUpdateFieldsScript("ctx._source", fieldKeys);
  }

  static String createUpdateFieldsScript(final String fieldPath, final Set<String> fieldKeys) {
    return fieldKeys
      .stream()
      .map(fieldKey -> String.format("%s.%s = params.%s;\n", fieldPath, fieldKey, fieldKey))
      .collect(Collectors.joining());
  }

}

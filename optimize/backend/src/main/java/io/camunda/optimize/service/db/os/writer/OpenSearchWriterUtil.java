/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.writer;

import static io.camunda.optimize.service.db.DatabaseConstants.OPTIMIZE_DATE_FORMAT;
import static io.camunda.optimize.service.db.writer.DatabaseWriterUtil.createUpdateFieldsScript;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.service.db.os.client.dsl.QueryDSL;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch._types.Script;
import org.slf4j.Logger;

public final class OpenSearchWriterUtil {

  public static final DateTimeFormatter DATE_TIME_FORMATTER =
      DateTimeFormatter.ofPattern(OPTIMIZE_DATE_FORMAT);

  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(OpenSearchWriterUtil.class);

  private OpenSearchWriterUtil() {}

  public static Script createFieldUpdateScript(
      final Set<String> fields, final Object entityDto, final ObjectMapper objectMapper) {
    final Map<String, JsonData> params =
        createFieldUpdateScriptParams(fields, entityDto, objectMapper);
    return createDefaultScriptWithPrimitiveParams(
        createUpdateFieldsScript(params.keySet()), params);
  }

  public static Script createDefaultScriptWithPrimitiveParams(
      final String inlineUpdateScript, final Map<String, JsonData> params) {
    return QueryDSL.scriptFromJsonData(inlineUpdateScript, params);
  }

  public static Script createDefaultScriptWithSpecificDtoParams(
      final String inlineUpdateScript, final Map<String, JsonData> params) {
    return QueryDSL.scriptFromJsonData(inlineUpdateScript, params);
  }

  public static Map<String, JsonData> createFieldUpdateScriptParams(
      final Set<String> fields, final Object entityDto, final ObjectMapper objectMapper) {
    final Map<String, Object> entityAsMap =
        objectMapper.convertValue(entityDto, new TypeReference<>() {});
    final Map<String, JsonData> params = new HashMap<>();
    for (final String fieldName : fields) {
      Object fieldValue = entityAsMap.get(fieldName);
      if (fieldValue != null) {
        if (fieldValue instanceof final TemporalAccessor temporalAccessor) {
          fieldValue = DATE_TIME_FORMATTER.format(temporalAccessor);
        }
        params.put(fieldName, JsonData.of(fieldValue));
      }
    }
    return params;
  }

  public static Script createDefaultScript(final String inlineUpdateScript) {
    return createDefaultScriptWithPrimitiveParams(inlineUpdateScript, Collections.emptyMap());
  }
}

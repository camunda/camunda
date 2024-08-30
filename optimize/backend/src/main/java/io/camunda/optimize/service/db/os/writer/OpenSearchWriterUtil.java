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
import io.camunda.optimize.service.db.os.externalcode.client.dsl.QueryDSL;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch._types.Script;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class OpenSearchWriterUtil {

  public static final DateTimeFormatter dateTimeFormatter =
      DateTimeFormatter.ofPattern(OPTIMIZE_DATE_FORMAT);

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
    Map<String, Object> entityAsMap =
        objectMapper.convertValue(entityDto, new TypeReference<>() {});
    final Map<String, JsonData> params = new HashMap<>();
    for (String fieldName : fields) {
      Object fieldValue = entityAsMap.get(fieldName);
      if (fieldValue != null) {
        if (fieldValue instanceof TemporalAccessor temporalAccessor) {
          fieldValue = dateTimeFormatter.format(temporalAccessor);
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

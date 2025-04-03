/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.writer;

import static io.camunda.optimize.service.db.writer.DatabaseWriterUtil.createFieldUpdateScriptParams;
import static io.camunda.optimize.service.db.writer.DatabaseWriterUtil.createUpdateFieldsScript;

import co.elastic.clients.elasticsearch._types.Script;
import co.elastic.clients.elasticsearch._types.ScriptLanguage;
import co.elastic.clients.json.JsonData;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ElasticsearchWriterUtil {

  public static Script createFieldUpdateScript(
      final Set<String> fields, final Object entityDto, final ObjectMapper objectMapper) {
    final Map<String, String> params =
        createFieldUpdateScriptParams(fields, entityDto, objectMapper);
    return createDefaultScriptWithPrimitiveParams(
        createUpdateFieldsScript(params.keySet()), params);
  }

  public static <T> Script createDefaultScriptWithPrimitiveParams(
      final String inlineUpdateScript, final Map<String, T> params) {
    return Script.of(
        i ->
            i.lang(ScriptLanguage.Painless)
                .source(inlineUpdateScript)
                .params(
                    params.entrySet().stream()
                        .collect(
                            Collectors.toMap(Map.Entry::getKey, e -> JsonData.of(e.getValue())))));
  }

  public static Script createDefaultScriptWithJsonParams(
      final String inlineUpdateScript, final Map<String, JsonData> params) {
    return Script.of(
        i -> i.lang(ScriptLanguage.Painless).source(inlineUpdateScript).params(params));
  }

  public static Script createDefaultScriptWithSpecificDtoParams(
      final String inlineUpdateScript, final Map<String, Object> params) {
    return Script.of(
        i -> {
          i.lang(ScriptLanguage.Painless).source(inlineUpdateScript);
          if (params != null) {
            i.params(
                params.entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, e -> JsonData.of(e.getValue()))));
          }
          return i;
        });
  }

  public static Script createDefaultScript(final String inlineUpdateScript) {
    return Script.of(i -> i.lang(ScriptLanguage.Painless).source(inlineUpdateScript));
  }
}

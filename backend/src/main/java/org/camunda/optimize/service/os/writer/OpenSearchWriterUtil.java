/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.os.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.os.externalcode.client.dsl.QueryDSL;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch._types.Script;

import java.util.Map;
import java.util.Set;

import static org.camunda.optimize.service.db.writer.DatabaseWriterUtil.createFieldUpdateScriptParams;
import static org.camunda.optimize.service.db.writer.DatabaseWriterUtil.createUpdateFieldsScript;
import static org.camunda.optimize.service.db.writer.DatabaseWriterUtil.mapParamsForScriptCreation;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class OpenSearchWriterUtil {

  public static Script createFieldUpdateScript(final Set<String> fields,
                                               final Object entityDto,
                                               final ObjectMapper objectMapper) {
    final Map<String, JsonData> params = createFieldUpdateScriptParams(fields, entityDto, objectMapper);
    return createDefaultScriptWithPrimitiveParams(createUpdateFieldsScript(params.keySet()), params);
  }

  public static Script createDefaultScriptWithPrimitiveParams(final String inlineUpdateScript,
                                                              final Map<String, JsonData> params) {
    return QueryDSL.scriptFromJsonData(inlineUpdateScript, params);
  }

  public static Script createDefaultScriptWithSpecificDtoParams(final String inlineUpdateScript,
                                                                final Map<String, JsonData> params,
                                                                final ObjectMapper objectMapper) {
    return QueryDSL.scriptFromJsonData(inlineUpdateScript, mapParamsForScriptCreation(params, objectMapper));
  }
}

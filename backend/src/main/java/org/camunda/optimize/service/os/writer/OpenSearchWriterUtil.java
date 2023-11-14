/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.os.writer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch._types.Script;

import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.camunda.optimize.service.db.DatabaseConstants.OPTIMIZE_DATE_FORMAT;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class OpenSearchWriterUtil {

  // TODO clean up this class with OPT-7229
  private static final String TASKS_ENDPOINT = "_tasks";
  private static final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(OPTIMIZE_DATE_FORMAT);
  private static final String NESTED_DOC_LIMIT_MESSAGE = "The number of nested documents has exceeded the allowed " +
    "limit of";

  public static Script createFieldUpdateScript(final Set<String> fields,
                                               final Object entityDto,
                                               final ObjectMapper objectMapper) {
//        final Map<String, JsonData> params = createFieldUpdateScriptParams(fields, entityDto, objectMapper);
//        return createDefaultScriptWithPrimitiveParams(
//                OpenSearchWriterUtil.createUpdateFieldsScript(params.keySet()),
//                params
//        );
    throw new NotImplementedException();
  }

  private static Map<String, JsonData> createFieldUpdateScriptParams(final Set<String> fields,
                                                                     final Object entityDto,
                                                                     final ObjectMapper objectMapper) {
    Map<String, Object> entityAsMap = objectMapper.convertValue(entityDto, new TypeReference<>() {
    });

    final Map<String, JsonData> params = new HashMap<>();
    for (String fieldName : fields) {
      Object fieldValue = entityAsMap.get(fieldName);
      if (fieldValue != null) {
        if (fieldValue instanceof TemporalAccessor) {
          fieldValue = dateTimeFormatter.format((TemporalAccessor) fieldValue);
        }
        params.put(fieldName, JsonData.of(fieldValue));
      }
    }
    return params;
  }

  public static Script createDefaultScriptWithPrimitiveParams(final String inlineUpdateScript,
                                                              final Map<String, JsonData> params) {
//        InlineScript inlineScript = new InlineScript.Builder()
//                .source(inlineUpdateScript)
//                //todo check langs
//                .lang("painless")
//                .params(params)
//                .build();
//
//        return new Script.Builder()
//                .inline(inlineScript)
//                .build();
    throw new NotImplementedException();
  }

  //
  public static Script createDefaultScriptWithSpecificDtoParams(final String inlineUpdateScript,
                                                                final Map<String, JsonData> params) {

//      InlineScript inlineScript = new InlineScript.Builder()
//              .source(inlineUpdateScript)
//              //todo check langs
//              .lang("painless")
//              .params(params)
//              .build();
//
//      return new Script.Builder()
//              .inline(inlineScript)
//              .build();
    throw new NotImplementedException();
  }
}

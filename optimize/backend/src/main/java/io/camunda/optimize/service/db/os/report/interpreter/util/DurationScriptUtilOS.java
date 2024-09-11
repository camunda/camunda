/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.interpreter.util;

import static io.camunda.optimize.service.db.os.writer.OpenSearchWriterUtil.createDefaultScriptWithPrimitiveParams;

import io.camunda.optimize.dto.optimize.query.report.single.process.filter.data.DurationFilterDataDto;
import io.camunda.optimize.service.db.os.writer.OpenSearchWriterUtil;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch._types.Script;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class DurationScriptUtilOS {
  public static Script getDurationScript(
      final long currRequestDateInMs,
      final String durationFieldName,
      final String referenceDateFieldName) {
    final Map<String, JsonData> params = new HashMap<>();
    return OpenSearchWriterUtil.createDefaultScriptWithPrimitiveParams(
        getDurationCalculationScriptPart(
                params, currRequestDateInMs, durationFieldName, referenceDateFieldName)
            + " return result;",
        params);
  }

  public static Script getDurationFilterScript(
      final long currRequestDateInMs,
      final String durationFieldName,
      final String referenceDateFieldName,
      final DurationFilterDataDto durationFilterDto) {
    final Map<String, JsonData> params = new HashMap<>();
    params.put("filterDuration", JsonData.of(getFilterDuration(durationFilterDto)));
    return createDefaultScriptWithPrimitiveParams(
        // All duration filters operate on totalDuration
        // --> no specific userTask calculations needed, can use the general duration script
        getDurationCalculationScriptPart(
                params, currRequestDateInMs, durationFieldName, referenceDateFieldName)
            + " return (result != null "
            + "&& result "
            + durationFilterDto.getOperator().getId()
            + " params['filterDuration'])"
            + " || ("
            + durationFilterDto.isIncludeNull()
            + " && result == null)",
        params);
  }

  private static long getFilterDuration(final DurationFilterDataDto durationFilterDto) {
    return ChronoUnit.valueOf(durationFilterDto.getUnit().name()).getDuration().toMillis()
        * durationFilterDto.getValue();
  }

  private static String getDurationCalculationScriptPart(
      final Map<String, JsonData> params,
      final long currRequestDateInMs,
      final String durationFieldName,
      final String referenceDateFieldName) {
    params.put("currRequestDateInMs", JsonData.of(currRequestDateInMs));
    params.put("durFieldName", JsonData.of(durationFieldName));
    params.put("refDateFieldName", JsonData.of(referenceDateFieldName));

    return """
        Long result;
        if (doc[params.durFieldName].empty && !doc[params.refDateFieldName].empty) {
        result = params.currRequestDateInMs - doc[params.refDateFieldName].value.toInstant().toEpochMilli()
        } else {
        result = !doc[params.durFieldName].empty ? doc[params.durFieldName].value : null
        }
        """;
  }
}

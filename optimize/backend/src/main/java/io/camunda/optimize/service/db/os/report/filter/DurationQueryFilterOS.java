/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.filter;

import static io.camunda.optimize.service.db.os.report.interpreter.util.DurationScriptUtilOS.getDurationFilterScript;

import io.camunda.optimize.dto.optimize.query.report.single.process.filter.data.DurationFilterDataDto;
import io.camunda.optimize.service.db.filter.FilterContext;
import io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex;
import io.camunda.optimize.service.security.util.LocalDateUtil;
import java.util.List;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.ScriptQuery;
import org.springframework.stereotype.Component;

@Component
public class DurationQueryFilterOS implements QueryFilterOS<DurationFilterDataDto> {

  @Override
  public List<Query> filterQueries(
      final List<DurationFilterDataDto> durations, final FilterContext filterContext) {
    if (durations == null) {
      return List.of();
    }

    return durations.stream()
        .map(
            durationDto ->
                new ScriptQuery.Builder()
                    .script(
                        getDurationFilterScript(
                            LocalDateUtil.getCurrentDateTime().toInstant().toEpochMilli(),
                            ProcessInstanceIndex.DURATION,
                            ProcessInstanceIndex.START_DATE,
                            durationDto))
                    .build()
                    .toQuery())
        .toList();
  }
}

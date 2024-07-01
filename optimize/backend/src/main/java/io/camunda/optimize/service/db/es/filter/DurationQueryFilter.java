/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.filter;

import static io.camunda.optimize.service.db.es.report.command.util.DurationScriptUtil.getDurationFilterScript;

import io.camunda.optimize.dto.optimize.query.report.single.process.filter.data.DurationFilterDataDto;
import io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex;
import io.camunda.optimize.service.security.util.LocalDateUtil;
import java.util.List;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.ScriptQueryBuilder;
import org.springframework.stereotype.Component;

@Component
public class DurationQueryFilter implements QueryFilter<DurationFilterDataDto> {

  @Override
  public void addFilters(
      final BoolQueryBuilder query,
      final List<DurationFilterDataDto> durations,
      final FilterContext filterContext) {
    if (durations != null && !durations.isEmpty()) {
      final List<QueryBuilder> filters = query.filter();

      for (final DurationFilterDataDto durationDto : durations) {
        final ScriptQueryBuilder scriptQueryBuilder =
            QueryBuilders.scriptQuery(
                getDurationFilterScript(
                    LocalDateUtil.getCurrentDateTime().toInstant().toEpochMilli(),
                    ProcessInstanceIndex.DURATION,
                    ProcessInstanceIndex.START_DATE,
                    durationDto));

        filters.add(scriptQueryBuilder);
      }
    }
  }
}

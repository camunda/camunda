/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.filter;

import org.camunda.optimize.dto.optimize.query.report.single.process.filter.data.DurationFilterDataDto;
import org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.ScriptQueryBuilder;
import org.springframework.stereotype.Component;

import java.util.List;

import static org.camunda.optimize.service.es.report.command.util.DurationScriptUtil.getDurationFilterScript;


@Component
public class DurationQueryFilter implements QueryFilter<DurationFilterDataDto> {

  public void addFilters(final BoolQueryBuilder query,
                         final List<DurationFilterDataDto> durations,
                         final FilterContext filterContext) {
    if (durations != null && !durations.isEmpty()) {
      List<QueryBuilder> filters = query.filter();

      for (DurationFilterDataDto durationDto : durations) {
        ScriptQueryBuilder scriptQueryBuilder = QueryBuilders.scriptQuery(getDurationFilterScript(
          LocalDateUtil.getCurrentDateTime().toInstant().toEpochMilli(),
          ProcessInstanceIndex.DURATION,
          ProcessInstanceIndex.START_DATE,
          durationDto
        ));

        filters.add(scriptQueryBuilder);
      }
    }
  }

}

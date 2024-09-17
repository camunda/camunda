/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.filter;

import static io.camunda.optimize.service.db.es.report.interpreter.util.DurationScriptUtilES.getDurationFilterScript;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.data.DurationFilterDataDto;
import io.camunda.optimize.service.db.filter.FilterContext;
import io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex;
import io.camunda.optimize.service.security.util.LocalDateUtil;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class DurationQueryFilterES implements QueryFilterES<DurationFilterDataDto> {

  @Override
  public void addFilters(
      final BoolQuery.Builder query,
      final List<DurationFilterDataDto> durations,
      final FilterContext filterContext) {
    if (durations != null && !durations.isEmpty()) {
      for (final DurationFilterDataDto durationDto : durations) {
        query.filter(
            f ->
                f.script(
                    s ->
                        s.script(
                            getDurationFilterScript(
                                LocalDateUtil.getCurrentDateTime().toInstant().toEpochMilli(),
                                ProcessInstanceIndex.DURATION,
                                ProcessInstanceIndex.START_DATE,
                                durationDto))));
      }
    }
  }
}

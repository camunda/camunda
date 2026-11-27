/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.filter;

import io.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import java.util.List;

/**
 * A process filter enhancer that also exposes its instance start and end date filters, so a date
 * histogram can extend and limit its buckets to the filtered date range.
 */
public interface InstanceDateQueryFilterEnhancerOS
    extends QueryFilterEnhancerOS<ProcessFilterDto<?>> {

  <T extends FilterDataDto> List<T> extractInstanceFilters(
      List<ProcessFilterDto<?>> filters, Class<? extends ProcessFilterDto<T>> clazz);

  QueryFilterOS<DateFilterDataDto<?>> getInstanceStartDateQueryFilter();

  QueryFilterOS<DateFilterDataDto<?>> getInstanceEndDateQueryFilter();
}

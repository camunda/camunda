/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.filter;

import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterDataDto;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.END_DATE;

@Component
public class EndDateQueryFilter extends DateQueryFilter {

  @Autowired
  public EndDateQueryFilter(final DateTimeFormatter formatter) {
    super(formatter);
  }

  @Override
  public void addFilters(BoolQueryBuilder query, List<DateFilterDataDto> filter) {
    addFilters(query, filter, END_DATE);
  }
}

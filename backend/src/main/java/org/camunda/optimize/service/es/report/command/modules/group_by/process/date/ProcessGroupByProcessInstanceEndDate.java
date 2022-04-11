/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.command.modules.group_by.process.date;

import org.camunda.optimize.dto.optimize.query.report.single.process.group.EndDateGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.value.DateGroupByValueDto;
import org.camunda.optimize.service.es.filter.ProcessQueryFilterEnhancer;
import org.camunda.optimize.service.es.report.MinMaxStatsService;
import org.camunda.optimize.service.es.report.command.service.DateAggregationService;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.END_DATE;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ProcessGroupByProcessInstanceEndDate extends AbstractProcessGroupByProcessInstanceDate {

  protected ProcessGroupByProcessInstanceEndDate(final ConfigurationService configurationService,
                                                 final DateAggregationService dateAggregationService,
                                                 final MinMaxStatsService minMaxStatsService,
                                                 final ProcessQueryFilterEnhancer processQueryFilterEnhancer) {
    super(
      configurationService,
      dateAggregationService,
      minMaxStatsService,
      processQueryFilterEnhancer
    );
  }

  @Override
  protected ProcessGroupByDto<DateGroupByValueDto> getGroupByType() {
    return new EndDateGroupByDto();
  }

  @Override
  public String getDateField() {
    return END_DATE;
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import org.apache.commons.lang3.Range;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.filter.QueryFilterEnhancer;
import org.camunda.optimize.service.es.reader.DecisionDefinitionReader;
import org.camunda.optimize.service.es.reader.ProcessDefinitionReader;
import org.camunda.optimize.service.es.report.command.util.IntervalAggregationService;
import org.camunda.optimize.service.util.configuration.ConfigurationService;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.camunda.optimize.service.es.report.SingleReportEvaluator.DEFAULT_RECORD_LIMIT;

@Data
public class CommandContext<T extends ReportDefinitionDto> {

  // this first block will be remove later on
  private OptimizeElasticsearchClient esClient;
  private ConfigurationService configurationService;
  private ObjectMapper objectMapper;
  private QueryFilterEnhancer queryFilterEnhancer;
  private IntervalAggregationService intervalAggregationService;
  private ProcessDefinitionReader processDefinitionReader;
  private DecisionDefinitionReader decisionDefinitionReader;

  private T reportDefinition;
  private Integer recordLimit = DEFAULT_RECORD_LIMIT;

  // only used/needed for group by date commands when evaluated for
  // a combined report.
  private Range<OffsetDateTime> dateIntervalRange;

  public void setRecordLimit(final Integer recordLimit) {
    this.recordLimit = Optional.ofNullable(recordLimit).orElse(DEFAULT_RECORD_LIMIT);
  }
}

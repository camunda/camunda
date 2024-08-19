/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.command;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.query.report.CommandEvaluationResult;
import io.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import io.camunda.optimize.service.db.es.report.ReportEvaluationContext;
import io.camunda.optimize.service.exceptions.OptimizeValidationException;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

@Component
public class NotSupportedCommand implements Command<Object, ReportDefinitionDto<?>> {

  private static final Logger log = org.slf4j.LoggerFactory.getLogger(NotSupportedCommand.class);
  private final ObjectMapper objectMapper;

  public NotSupportedCommand(final ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public CommandEvaluationResult<Object> evaluate(
      final ReportEvaluationContext<ReportDefinitionDto<?>> reportEvaluationContext) {
    // Error should contain the report Name
    try {
      log.warn(
          "The following settings combination of the report data is not supported in Optimize: \n"
              + "{} \n "
              + "Therefore returning error result.",
          objectMapper.writeValueAsString(reportEvaluationContext.getReportDefinition()));
    } catch (final JsonProcessingException e) {
      log.error("can't serialize report data", e);
    }
    throw new OptimizeValidationException(
        "This combination of the settings of the report builder is not supported!");
  }

  @Override
  public String createCommandKey() {
    // could be anything, we don't care
    return "not_supported";
  }
}

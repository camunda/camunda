/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.report;

import static java.lang.String.format;
import static java.util.function.Function.identity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import io.camunda.optimize.service.db.report.plan.ExecutionPlan;
import io.camunda.optimize.service.db.report.plan.decision.DecisionExecutionPlan;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.exceptions.OptimizeValidationException;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class ExecutionPlanExtractor {

  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(ExecutionPlanExtractor.class);
  final ApplicationContext applicationContext;
  final ConfigurationService configurationService;
  final Map<String, ExecutionPlan> executionPlans = new HashMap<>();
  private final ObjectMapper objectMapper;

  public ExecutionPlanExtractor(
      final ApplicationContext applicationContext,
      final ConfigurationService configurationService,
      final @Qualifier("optimizeObjectMapper") ObjectMapper objectMapper) {
    this.applicationContext = applicationContext;
    this.configurationService = configurationService;
    this.objectMapper = objectMapper;

    final boolean isAssigneeAnalyticsEnabled =
        configurationService.getUiConfiguration().isUserTaskAssigneeAnalyticsEnabled();

    executionPlans.putAll(
        Arrays.stream(ProcessExecutionPlan.values())
            .filter(plan -> isAssigneeAnalyticsEnabled || !plan.isAssigneeReport())
            .collect(Collectors.toMap(ExecutionPlan::getCommandKey, identity())));

    executionPlans.putAll(
        Arrays.stream(DecisionExecutionPlan.values())
            .collect(Collectors.toMap(ExecutionPlan::getCommandKey, identity())));
  }

  public <R extends ReportDefinitionDto<?>> List<ExecutionPlan> extractExecutionPlans(
      final R reportDefinition) {
    return reportDefinition.getData().createCommandKeys().stream()
        .map(commandKey -> resolve(commandKey, reportDefinition))
        .toList();
  }

  private <R extends ReportDefinitionDto<?>> ExecutionPlan resolve(
      final String commandKey, final R reportDefinition) {
    return Optional.ofNullable(executionPlans.get(commandKey))
        .orElseThrow(() -> unsupportedError(commandKey, reportDefinition));
  }

  private <R extends ReportDefinitionDto<?>> OptimizeValidationException unsupportedError(
      final String commandKey, final R reportDefinition) {
    // Error should contain the report Name
    try {
      LOG.warn(
          format(
              """
                  The following settings combination of the report data is not supported in Optimize (commandKey=%s):
                  {}
                  Therefore returning error result.""",
              commandKey),
          objectMapper.writeValueAsString(reportDefinition));
    } catch (final JsonProcessingException e) {
      LOG.error("Failed to serialize report definition!", e);
    }
    throw new OptimizeValidationException(
        "This combination of the settings of the report builder is not supported!");
  }
}

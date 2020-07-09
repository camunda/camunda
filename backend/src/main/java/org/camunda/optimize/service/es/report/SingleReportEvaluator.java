/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report;

import lombok.RequiredArgsConstructor;
import org.camunda.optimize.dto.optimize.ReportType;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.ReportEvaluationResult;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.service.es.report.command.Command;
import org.camunda.optimize.service.es.report.command.CommandContext;
import org.camunda.optimize.service.es.report.command.NotSupportedCommand;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.util.ValidationHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.es.filter.DateFilterQueryService.truncateDateFiltersToStartOfDay;

@RequiredArgsConstructor
@Component
public class SingleReportEvaluator {

  public static final Integer DEFAULT_RECORD_LIMIT = 1_000;

  protected final NotSupportedCommand notSupportedCommand;
  protected final ApplicationContext applicationContext;
  protected final Map<String, Command> commandSuppliers;

  @Autowired
  public SingleReportEvaluator(final NotSupportedCommand notSupportedCommand,
                               final ApplicationContext applicationContext,
                               final Collection<Command> commands) {
    this(
      notSupportedCommand,
      applicationContext,
      commands.stream()
        .collect(Collectors.toMap(Command::createCommandKey, c -> applicationContext.getBean(c.getClass())))
    );
  }

  <T extends ReportDefinitionDto<?>> ReportEvaluationResult<?, T> evaluate(CommandContext<T> commandContext)
    throws OptimizeException {
    // Currently, Optimize does not support time settings in DateFilters, so truncate all date filters to day
    truncateDateFiltersInReport(commandContext.getReportDefinition());
    Command<T> evaluationCommand = extractCommandWithValidation(commandContext.getReportDefinition());
    return evaluationCommand.evaluate(commandContext);
  }

  private <T extends ReportDefinitionDto<?>> Command<T> extractCommandWithValidation(T reportDefinition) {
    ValidationHelper.validate(reportDefinition.getData());
    return extractCommand(reportDefinition);
  }

  @SuppressWarnings(value = "unchecked")
  <T extends ReportDefinitionDto<?>> Command<T> extractCommand(T reportDefinition) {
    return commandSuppliers.getOrDefault(reportDefinition.getData().createCommandKey(), notSupportedCommand);
  }

  private void truncateDateFiltersInReport(final ReportDefinitionDto reportDefinition) {
    if (reportDefinition.getCombined()) {
      // reports in combined reports will have already been saved and had their filters truncated then
      return;
    }
    List<FilterDataDto> filters;
    if (ReportType.PROCESS.equals(reportDefinition.getReportType())) {
      filters = ((SingleProcessReportDefinitionDto) reportDefinition).getFilterData();
    } else {
      filters = ((SingleDecisionReportDefinitionDto) reportDefinition).getFilterData();
    }
    truncateDateFiltersToStartOfDay(filters);
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report;

import lombok.RequiredArgsConstructor;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.ReportEvaluationResult;
import org.camunda.optimize.dto.optimize.rest.pagination.PaginationDto;
import org.camunda.optimize.service.es.report.command.Command;
import org.camunda.optimize.service.es.report.command.CommandContext;
import org.camunda.optimize.service.es.report.command.NotSupportedCommand;
import org.camunda.optimize.service.es.report.command.decision.raw.RawDecisionInstanceDataGroupByNoneCmd;
import org.camunda.optimize.service.es.report.command.process.processinstance.raw.RawProcessInstanceDataGroupByNoneCmd;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.util.ValidationHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class SingleReportEvaluator {

  public static final int DEFAULT_LIMIT = 20;
  public static final int DEFAULT_OFFSET = 0;

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
    Command<T> evaluationCommand = extractCommandWithValidation(commandContext);
    return evaluationCommand.evaluate(commandContext);
  }

  private <T extends ReportDefinitionDto<?>> Command<T> extractCommandWithValidation(CommandContext<T> commandContext) {
    final T reportDefinition = commandContext.getReportDefinition();
    ValidationHelper.validate(reportDefinition.getData());
    final Command<T> command = extractCommand(reportDefinition);
    if (isRawDataReport(command)) {
      addDefaultPaginationForMissingValues(commandContext);
    }
    return command;
  }

  private <T extends ReportDefinitionDto<?>> void addDefaultPaginationForMissingValues(CommandContext<T> commandContext) {
    int offset = Optional.ofNullable(commandContext.getPagination())
      .filter(pag -> pag.getOffset() != null)
      .map(PaginationDto::getOffset)
      .orElse(DEFAULT_OFFSET);
    int limit = Optional.ofNullable(commandContext.getPagination())
      .filter(pag -> pag.getLimit() != null)
      .map(PaginationDto::getLimit)
      .orElse(DEFAULT_LIMIT);
    PaginationDto completePagination = new PaginationDto();
    completePagination.setOffset(offset);
    completePagination.setLimit(limit);
    commandContext.setPagination(completePagination);
  }

  private <T extends ReportDefinitionDto<?>> boolean isRawDataReport(final Command<T> command) {
    return command instanceof RawDecisionInstanceDataGroupByNoneCmd
      || command instanceof RawProcessInstanceDataGroupByNoneCmd;
  }

  @SuppressWarnings(value = "unchecked")
  <T extends ReportDefinitionDto<?>> Command<T> extractCommand(T reportDefinition) {
    return commandSuppliers.getOrDefault(reportDefinition.getData().createCommandKey(), notSupportedCommand);
  }
}

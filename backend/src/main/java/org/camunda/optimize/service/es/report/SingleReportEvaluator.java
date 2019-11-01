/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report;

import lombok.RequiredArgsConstructor;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.ReportEvaluationResult;
import org.camunda.optimize.service.es.report.command.Command;
import org.camunda.optimize.service.es.report.command.CommandContext;
import org.camunda.optimize.service.es.report.command.NotSupportedCommand;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.util.ValidationHelper;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@Component
public class SingleReportEvaluator {

  public static final Integer DEFAULT_RECORD_LIMIT = 1_000;

  private static Map<String, Command> commandSuppliers = new HashMap<>();

  protected final NotSupportedCommand notSupportedCommand;
  protected final ApplicationContext applicationContext;

  protected final List<Command> commands;

  @PostConstruct
  public void init() {
    commands.forEach(
      c -> commandSuppliers.put(
        c.createCommandKey(), applicationContext.getBean(c.getClass())
      )
    );
  }

  <T extends ReportDefinitionDto> ReportEvaluationResult<?, T> evaluate(CommandContext<T> commandContext)
    throws OptimizeException {
    Command<T> evaluationCommand = extractCommandWithValidation(commandContext.getReportDefinition());
    return evaluationCommand.evaluate(commandContext);
  }

  private <T extends ReportDefinitionDto> Command<T> extractCommandWithValidation(T reportDefinition) {
    ValidationHelper.validate(reportDefinition.getData());
    return extractCommand(reportDefinition);
  }

  @SuppressWarnings(value = "unchecked")
  <T extends ReportDefinitionDto> Command<T> extractCommand(T reportDefinition) {
    return commandSuppliers.getOrDefault(reportDefinition.getData().createCommandKey(), notSupportedCommand);
  }
}

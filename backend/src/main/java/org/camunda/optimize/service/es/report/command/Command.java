/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command;

import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.ReportEvaluationResult;
import org.camunda.optimize.service.es.report.MinMaxStatDto;
import org.camunda.optimize.service.exceptions.OptimizeException;

import java.util.Optional;

public interface Command<RD extends ReportDefinitionDto<?>> {

  ReportEvaluationResult<?, RD> evaluate(CommandContext<RD> commandContext) throws OptimizeException;

  String createCommandKey();

  /**
   * This method is used for *combined* grouped by commands to calculate the total data range.
   * This allows to calculate the same bucket interval for each single report in the combined report.
   * By default it's assumed that there is no range to be calculated, this needs to be implemented
   * in corresponding commands.
   *
   * @param commandContext the command context to perform the min max retrieval with
   * @return the min max stats for the commands groupBy, empty if not available or not implemented
   */
  default Optional<MinMaxStatDto> getGroupByMinMaxStats(final CommandContext<RD> commandContext) {
    return Optional.empty();
  }

}

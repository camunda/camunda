/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.command;

import io.camunda.optimize.dto.optimize.query.report.CommandEvaluationResult;
import io.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import io.camunda.optimize.service.db.es.report.ReportEvaluationContext;
import io.camunda.optimize.service.exceptions.OptimizeException;

public interface Command<T, R extends ReportDefinitionDto<?>> {

  CommandEvaluationResult<T> evaluate(ReportEvaluationContext<R> reportEvaluationContext)
      throws OptimizeException;

  String createCommandKey();

  default boolean isAssigneeReport() {
    return false;
  }
}

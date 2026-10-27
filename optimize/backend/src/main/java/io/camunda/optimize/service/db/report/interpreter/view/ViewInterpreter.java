/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.report.interpreter.view;

import io.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.plan.ExecutionPlan;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult.ViewResult;

public interface ViewInterpreter<DATA extends SingleReportDataDto, PLAN extends ExecutionPlan> {
  ViewProperty getViewProperty(final ExecutionContext<DATA, PLAN> context);

  ViewResult createEmptyResult(final ExecutionContext<DATA, PLAN> context);
}

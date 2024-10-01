/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.report;

import io.camunda.optimize.dto.optimize.query.report.SingleReportDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import io.camunda.optimize.service.db.report.plan.ExecutionPlan;

public class ExecutionContextFactory {
  public static <
          DATA extends SingleReportDataDto,
          PLAN extends ExecutionPlan,
          DEFINITION extends SingleReportDefinitionDto<DATA>>
      ExecutionContext<DATA, PLAN> buildExecutionContext(
          final PLAN plan, final ReportEvaluationContext<DEFINITION> context) {
    return new ExecutionContext<>(context, plan);
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.interpreter.view.process;

import io.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.db.os.report.interpreter.view.ViewInterpreterOS;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.db.report.plan.process.ProcessView;
import java.util.Set;

public interface ProcessViewInterpreterOS
    extends ViewInterpreterOS<ProcessReportDataDto, ProcessExecutionPlan> {
  Set<ProcessView> getSupportedViews();

  @Override
  default ViewProperty getViewProperty(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    return context.getPlan().getView().getProcessViewDto().getFirstProperty();
  }
}

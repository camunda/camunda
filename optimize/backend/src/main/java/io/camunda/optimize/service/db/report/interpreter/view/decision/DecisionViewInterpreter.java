/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.report.interpreter.view.decision;

import io.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import io.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.interpreter.view.ViewInterpreter;
import io.camunda.optimize.service.db.report.plan.decision.DecisionExecutionPlan;
import io.camunda.optimize.service.db.report.plan.decision.DecisionView;
import java.util.List;
import java.util.Set;

public interface DecisionViewInterpreter
    extends ViewInterpreter<DecisionReportDataDto, DecisionExecutionPlan> {
  Set<DecisionView> getSupportedViews();

  @Override
  default ViewProperty getViewProperty(
      final ExecutionContext<DecisionReportDataDto, DecisionExecutionPlan> context) {
    final List<ViewProperty> properties =
        context.getPlan().getView().getDecisionViewDto().getProperties();
    return properties != null && !properties.isEmpty() ? properties.get(0) : null;
  }
}

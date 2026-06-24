/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.interpreter.groupby.decision;

import io.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import io.camunda.optimize.service.db.os.report.interpreter.groupby.GroupByInterpreterOS;
import io.camunda.optimize.service.db.report.plan.decision.DecisionExecutionPlan;
import io.camunda.optimize.service.db.report.plan.decision.DecisionGroupBy;
import java.util.Set;

public interface DecisionGroupByInterpreterOS
    extends GroupByInterpreterOS<DecisionReportDataDto, DecisionExecutionPlan> {
  Set<DecisionGroupBy> getSupportedGroupBys();
}

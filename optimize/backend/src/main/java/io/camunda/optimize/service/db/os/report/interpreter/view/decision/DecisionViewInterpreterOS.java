/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.interpreter.view.decision;

import io.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import io.camunda.optimize.service.db.os.report.interpreter.view.ViewInterpreterOS;
import io.camunda.optimize.service.db.report.interpreter.view.decision.DecisionViewInterpreter;
import io.camunda.optimize.service.db.report.plan.decision.DecisionExecutionPlan;

public interface DecisionViewInterpreterOS
    extends ViewInterpreterOS<DecisionReportDataDto, DecisionExecutionPlan>,
        DecisionViewInterpreter {}
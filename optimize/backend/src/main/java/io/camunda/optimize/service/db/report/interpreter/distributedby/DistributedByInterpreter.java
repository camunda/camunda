/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.report.interpreter.distributedby;

import io.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.plan.ExecutionPlan;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult.DistributedByResult;
import java.util.List;

public interface DistributedByInterpreter<
    DATA extends SingleReportDataDto, PLAN extends ExecutionPlan> {
  default boolean isKeyOfNumericType(final ExecutionContext<DATA, PLAN> context) {
    return false;
  }

  List<DistributedByResult> createEmptyResult(final ExecutionContext<DATA, PLAN> context);
}

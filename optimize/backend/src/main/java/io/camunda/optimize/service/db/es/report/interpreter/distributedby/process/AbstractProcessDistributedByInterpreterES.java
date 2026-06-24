/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.interpreter.distributedby.process;

import static io.camunda.optimize.service.db.report.result.CompositeCommandResult.DistributedByResult.createDistributedByResult;

import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.db.es.report.interpreter.distributedby.AbstractDistributedByInterpreterES;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult.DistributedByResult;
import java.util.List;
import java.util.stream.Collectors;

public abstract class AbstractProcessDistributedByInterpreterES
    extends AbstractDistributedByInterpreterES<ProcessReportDataDto, ProcessExecutionPlan>
    implements ProcessDistributedByInterpreterES {

  @Override
  public List<DistributedByResult> createEmptyResult(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    return context.getAllDistributedByKeysAndLabels().entrySet().stream()
        .map(
            entry ->
                createDistributedByResult(
                    entry.getKey(),
                    entry.getValue(),
                    getViewInterpreter().createEmptyResult(context)))
        .collect(Collectors.toList());
  }
}

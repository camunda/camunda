/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.interpreter.groupby.process;

import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.db.os.report.interpreter.groupby.GroupByInterpreterOS;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.MinMaxStatDto;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.db.report.plan.process.ProcessGroupBy;
import java.util.Optional;
import java.util.Set;
import org.opensearch.client.opensearch._types.query_dsl.Query;

public interface ProcessGroupByInterpreterOS
    extends GroupByInterpreterOS<ProcessReportDataDto, ProcessExecutionPlan> {
  Set<ProcessGroupBy> getSupportedGroupBys();

  /**
   * This method returns the min and maximum values for range value types (e.g. number or date). It
   * defaults to an empty result and needs to get overridden when applicable.
   *
   * @param context command execution context to perform the min max retrieval with
   * @param baseQuery filtering query on which data to perform the min max retrieval
   * @return min and max value range for the value grouped on by
   */
  default Optional<MinMaxStatDto> getMinMaxStats(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context,
      final Query baseQuery) {
    return Optional.empty();
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

package io.camunda.optimize.service.db.os.report.interpreter.groupby.process.none;

import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.db.os.report.interpreter.RawResult;
import io.camunda.optimize.service.db.os.report.interpreter.distributedby.process.ProcessDistributedByInterpreterFacadeOS;
import io.camunda.optimize.service.db.os.report.interpreter.groupby.process.AbstractProcessGroupByInterpreterOS;
import io.camunda.optimize.service.db.os.report.interpreter.groupby.process.ProcessGroupByInterpreterOS;
import io.camunda.optimize.service.db.os.report.interpreter.view.process.ProcessViewInterpreterFacadeOS;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.db.report.plan.process.ProcessGroupBy;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult.DistributedByResult;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult.GroupByResult;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Conditional(OpenSearchCondition.class)
public class ProcessGroupByNoneInterpreterOS extends AbstractProcessGroupByInterpreterOS
    implements ProcessGroupByInterpreterOS {
  @Getter private final ProcessDistributedByInterpreterFacadeOS distributedByInterpreter;
  @Getter private final ProcessViewInterpreterFacadeOS viewInterpreter;

  @Override
  public Set<ProcessGroupBy> getSupportedGroupBys() {
    return Set.of(ProcessGroupBy.PROCESS_GROUP_BY_NONE);
  }

  @Override
  public Map<String, Aggregation> createAggregation(
      final Query baseQuery,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    // nothing to do here, since we don't group so just pass the view part on
    return distributedByInterpreter.createAggregations(context, baseQuery);
  }

  @Override
  public void addQueryResult(
      final CompositeCommandResult compositeCommandResult,
      final SearchResponse<RawResult> response,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final List<DistributedByResult> distributions =
        distributedByInterpreter.retrieveResult(response, response.aggregations(), context);
    GroupByResult groupByResult = GroupByResult.createGroupByNone(distributions);
    compositeCommandResult.setGroup(groupByResult);
  }
}

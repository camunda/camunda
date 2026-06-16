/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.interpreter.plan.process;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import io.camunda.optimize.dto.optimize.query.report.single.ReportDataDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import io.camunda.optimize.service.db.os.report.filter.ProcessQueryFilterEnhancerOS;
import io.camunda.optimize.service.db.os.report.interpreter.groupby.process.ProcessGroupByInterpreterFacadeOS;
import io.camunda.optimize.service.db.os.report.interpreter.view.process.ProcessViewInterpreterFacadeOS;
import io.camunda.optimize.service.db.reader.ProcessDefinitionReader;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;

@ExtendWith(MockitoExtension.class)
class GenericProcessExecutionPlanInterpreterOSTest {

  @Mock private ProcessDefinitionReader processDefinitionReader;
  @Mock private OptimizeOpenSearchClient osClient;
  @Mock private ProcessQueryFilterEnhancerOS queryFilterEnhancer;
  @Mock private ProcessGroupByInterpreterFacadeOS groupByInterpreter;
  @Mock private ProcessViewInterpreterFacadeOS viewInterpreter;
  @Mock private ConfigurationService configurationService;

  @SuppressWarnings("unchecked")
  @Mock
  private ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context;

  private GenericProcessExecutionPlanInterpreterOS underTest;

  @BeforeEach
  void setUp() {
    underTest =
        new GenericProcessExecutionPlanInterpreterOS(
            processDefinitionReader,
            osClient,
            queryFilterEnhancer,
            groupByInterpreter,
            viewInterpreter,
            configurationService);
  }

  @Test
  void shouldExcludeAllInstancesForManagementReportWithNoDefinitions() {
    // given a management report whose user has access to no definitions
    // (lenient: the management branch does not apply filterQueries today, but stubbing guards
    // against an NPE should it start to)
    lenient().when(queryFilterEnhancer.filterQueries(any(), any())).thenReturn(List.of());
    final BoolQuery baseQuery = buildBaseQuery(reportData(true, List.of()));

    // then all instances are excluded via a (not match-all) filter clause
    assertThat(baseQuery.filter())
        .anySatisfy(
            query -> {
              assertThat(query._kind()).isEqualTo(Query.Kind.Bool);
              assertThat(query.bool().mustNot())
                  .anySatisfy(
                      mustNot -> assertThat(mustNot._kind()).isEqualTo(Query.Kind.MatchAll));
            });
  }

  @Test
  void shouldNotExcludeAllInstancesForNonManagementReportWithNoDefinitions() {
    // given a non-management report with no definitions (e.g. a seeded dashboard report)
    when(queryFilterEnhancer.filterQueries(any(), any())).thenReturn(List.of());
    final BoolQuery baseQuery = buildBaseQuery(reportData(false, List.of()));

    // then no exclude-all clause and no definition-scoping clause is added, so other query filters
    // apply across all instances
    assertThat(baseQuery.filter()).isEmpty();
    assertThat(baseQuery.should()).isEmpty();
    assertThat(baseQuery.minimumShouldMatch()).isNull();
  }

  private BoolQuery buildBaseQuery(final ProcessReportDataDto reportData) {
    when(context.getReportData()).thenReturn(reportData);
    return underTest.baseQueryBuilder(context).build();
  }

  private static ProcessReportDataDto reportData(
      final boolean managementReport, final List<ReportDataDefinitionDto> definitions) {
    final ProcessReportDataDto reportData = new ProcessReportDataDto();
    reportData.setManagementReport(managementReport);
    reportData.setDefinitions(definitions);
    return reportData;
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.interpreter.plan.process;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import io.camunda.optimize.dto.optimize.query.report.single.ReportDataDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.es.filter.ProcessQueryFilterEnhancerES;
import io.camunda.optimize.service.db.es.report.interpreter.groupby.process.ProcessGroupByInterpreterFacadeES;
import io.camunda.optimize.service.db.es.report.interpreter.view.process.ProcessViewInterpreterFacadeES;
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

@ExtendWith(MockitoExtension.class)
class GenericProcessExecutionPlanInterpreterESTest {

  @Mock private ProcessDefinitionReader processDefinitionReader;
  @Mock private OptimizeElasticsearchClient esClient;
  @Mock private ProcessQueryFilterEnhancerES queryFilterEnhancer;
  @Mock private ProcessGroupByInterpreterFacadeES groupByInterpreter;
  @Mock private ProcessViewInterpreterFacadeES viewInterpreter;
  @Mock private ConfigurationService configurationService;

  @SuppressWarnings("unchecked")
  @Mock
  private ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context;

  private GenericProcessExecutionPlanInterpreterES underTest;

  @BeforeEach
  void setUp() {
    underTest =
        new GenericProcessExecutionPlanInterpreterES(
            processDefinitionReader,
            esClient,
            queryFilterEnhancer,
            groupByInterpreter,
            viewInterpreter,
            configurationService);
  }

  @Test
  void shouldExcludeAllInstancesForManagementReportWithNoDefinitions() {
    // given a management report whose user has access to no definitions
    final BoolQuery baseQuery = buildBaseQuery(reportData(true, List.of()));

    // then all instances are excluded via a mustNot match-all clause
    assertThat(baseQuery.mustNot())
        .anySatisfy(query -> assertThat(query._kind()).isEqualTo(Query.Kind.MatchAll));
  }

  @Test
  void shouldNotExcludeAllInstancesForNonManagementReportWithNoDefinitions() {
    // given a non-management report with no definitions (e.g. a seeded dashboard report)
    final BoolQuery baseQuery = buildBaseQuery(reportData(false, List.of()));

    // then no exclude-all clause and no definition-scoping clause is added, so other query filters
    // apply across all instances
    assertThat(baseQuery.mustNot()).isEmpty();
    assertThat(baseQuery.should()).isEmpty();
    assertThat(baseQuery.minimumShouldMatch()).isNull();
  }

  private BoolQuery buildBaseQuery(final ProcessReportDataDto reportData) {
    when(context.getReportData()).thenReturn(reportData);
    return underTest.getBaseQueryBuilder(context).build();
  }

  private static ProcessReportDataDto reportData(
      final boolean managementReport, final List<ReportDataDefinitionDto> definitions) {
    final ProcessReportDataDto reportData = new ProcessReportDataDto();
    reportData.setManagementReport(managementReport);
    reportData.setDefinitions(definitions);
    return reportData;
  }
}

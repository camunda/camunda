/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.interpreter.plan.process;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import io.camunda.optimize.dto.optimize.query.report.single.ReportDataDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.db.es.filter.ProcessQueryFilterEnhancerES;
import io.camunda.optimize.service.db.reader.ProcessDefinitionReader;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class AbstractProcessExecutionPlanInterpreterESTest {

  private AbstractProcessExecutionPlanInterpreterES underTest;
  private ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    underTest = mock(AbstractProcessExecutionPlanInterpreterES.class, CALLS_REAL_METHODS);
    when(underTest.getQueryFilterEnhancer()).thenReturn(mock(ProcessQueryFilterEnhancerES.class));
    context = mock(ExecutionContext.class);
  }

  // The definition clauses are the only thing restricting the query to the user's tenants. Every
  // report flavor is covered because the regression in camunda/camunda#70131 was a guard that
  // only applied to management reports.
  @ParameterizedTest(name = "{0}")
  @MethodSource("reportFlavors")
  void shouldMatchNothingWhenDefinitionListIsEmpty(
      final String flavor, final Consumer<ProcessReportDataDto> flagSetter) {
    // given
    final ProcessReportDataDto reportData = new ProcessReportDataDto();
    flagSetter.accept(reportData);
    reportData.setDefinitions(List.of());
    when(context.getReportData()).thenReturn(reportData);

    // when
    final BoolQuery baseQuery = underTest.getBaseQueryBuilder(context).build();
    final BoolQuery baselineQuery = underTest.setupUnfilteredBaseQueryBuilder(context).build();

    // then
    assertMatchesNothing(baseQuery);
    assertMatchesNothing(baselineQuery);
  }

  @Test
  void shouldScopeQueryToDefinitionTenantsWhenDefinitionsArePresent() {
    // given
    when(underTest.getProcessDefinitionReader()).thenReturn(mock(ProcessDefinitionReader.class));
    final ProcessReportDataDto reportData = new ProcessReportDataDto();
    reportData.setBusinessValueReport(true);
    reportData.setDefinitions(
        List.of(new ReportDataDefinitionDto("invoice-process", List.of("tenant-a"))));
    when(context.getReportData()).thenReturn(reportData);

    // when
    final BoolQuery query = underTest.getBaseQueryBuilder(context).build();

    // then
    assertThat(query.mustNot()).isEmpty();
    assertThat(query.should())
        .singleElement()
        .satisfies(
            definitionQuery ->
                assertThat(definitionQuery.toString())
                    .contains("invoice-process")
                    .contains("tenant-a"));
  }

  private static void assertMatchesNothing(final BoolQuery query) {
    assertThat(query.mustNot()).singleElement().satisfies(q -> assertThat(q.isMatchAll()).isTrue());
  }

  private static Stream<Arguments> reportFlavors() {
    return Stream.of(
        Arguments.of("user report", (Consumer<ProcessReportDataDto>) data -> {}),
        Arguments.of(
            "business value report",
            (Consumer<ProcessReportDataDto>) data -> data.setBusinessValueReport(true)),
        Arguments.of(
            "agentic control report",
            (Consumer<ProcessReportDataDto>) data -> data.setAgenticControlReport(true)),
        Arguments.of(
            "management report",
            (Consumer<ProcessReportDataDto>) data -> data.setManagementReport(true)),
        Arguments.of(
            "instant preview report",
            (Consumer<ProcessReportDataDto>) data -> data.setInstantPreviewReport(true)));
  }
}

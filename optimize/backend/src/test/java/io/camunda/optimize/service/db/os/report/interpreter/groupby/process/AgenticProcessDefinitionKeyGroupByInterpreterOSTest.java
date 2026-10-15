/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.interpreter.groupby.process;

import static io.camunda.optimize.service.db.DatabaseConstants.PROCESS_DEFINITION_KEY_AGGREGATION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.optimize.dto.optimize.query.report.single.ReportDataDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.db.os.report.interpreter.RawResult;
import io.camunda.optimize.service.db.os.report.interpreter.distributedby.process.ProcessDistributedByInterpreterFacadeOS;
import io.camunda.optimize.service.db.os.report.interpreter.view.process.ProcessViewInterpreterFacadeOS;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.client.opensearch._types.aggregations.Aggregate;
import org.opensearch.client.opensearch._types.aggregations.StringTermsBucket;
import org.opensearch.client.opensearch.core.SearchResponse;

@ExtendWith(MockitoExtension.class)
class AgenticProcessDefinitionKeyGroupByInterpreterOSTest {

  @Mock private ConfigurationService configurationService;
  @Mock private ProcessDistributedByInterpreterFacadeOS distributedByInterpreter;
  @Mock private ProcessViewInterpreterFacadeOS viewInterpreter;

  @SuppressWarnings("rawtypes")
  @Mock
  private ExecutionContext context;

  private AgenticProcessDefinitionKeyGroupByInterpreterOS underTest;

  @BeforeEach
  void setUp() {
    underTest =
        new AgenticProcessDefinitionKeyGroupByInterpreterOS(
            configurationService, distributedByInterpreter, viewInterpreter);
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldLabelGroupsWithDefinitionNamesForAgenticReportsWithoutBusinessValueFlag() {
    // given an agentic control report, which never carries the business value flag
    final SearchResponse<RawResult> response =
        responseWithBuckets("invoice-agent", "support-agent", "orphan-agent");
    givenAgenticReport(
        definition("invoice-agent", "Invoice Approval Agent"),
        definition("support-agent", "Support Agent"));

    // when
    final CompositeCommandResult result =
        new CompositeCommandResult(new ProcessReportDataDto(), ViewProperty.FREQUENCY);
    underTest.addQueryResult(result, response, context);

    // then known keys are labelled with their names and the unknown key falls back to the id
    assertThat(result.getGroups())
        .extracting(
            CompositeCommandResult.GroupByResult::getKey,
            CompositeCommandResult.GroupByResult::getLabel)
        .containsExactly(
            tuple("invoice-agent", "Invoice Approval Agent"),
            tuple("support-agent", "Support Agent"),
            tuple("orphan-agent", "orphan-agent"));
  }

  @SuppressWarnings("unchecked")
  private SearchResponse<RawResult> responseWithBuckets(final String... keys) {
    final SearchResponse<RawResult> response = mock(SearchResponse.class);
    when(response.aggregations())
        .thenReturn(Map.of(PROCESS_DEFINITION_KEY_AGGREGATION, stringTermsAggregate(keys)));
    when(distributedByInterpreter.retrieveResult(any(), any(), any())).thenReturn(List.of());
    when(distributedByInterpreter.isKeyOfNumericType(any())).thenReturn(false);
    return response;
  }

  @SuppressWarnings("unchecked")
  private void givenAgenticReport(final ReportDataDefinitionDto... definitions) {
    final ProcessReportDataDto reportData = new ProcessReportDataDto();
    reportData.setAgenticControlReport(true);
    reportData.setDefinitions(List.of(definitions));
    when(context.getReportData()).thenReturn(reportData);
  }

  private static ReportDataDefinitionDto definition(final String key, final String displayName) {
    final ReportDataDefinitionDto definition = new ReportDataDefinitionDto(key);
    definition.setName(key);
    definition.setDisplayName(displayName);
    return definition;
  }

  private static Aggregate stringTermsAggregate(final String... keys) {
    return Aggregate.of(
        a ->
            a.sterms(
                st ->
                    st.sumOtherDocCount(0L)
                        .docCountErrorUpperBound(0L)
                        .buckets(
                            b ->
                                b.array(
                                    Arrays.stream(keys)
                                        .map(
                                            key ->
                                                StringTermsBucket.of(
                                                    sb ->
                                                        sb.key(key)
                                                            .docCount(1L)
                                                            .aggregations(Map.of())))
                                        .toList()))));
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.interpreter.groupby.process;

import static io.camunda.optimize.service.db.report.plan.process.ProcessGroupBy.PROCESS_GROUP_BY_PROCESS_DEFINITION_VERSION;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.PROCESS_DEFINITION_VERSION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.db.os.report.interpreter.RawResult;
import io.camunda.optimize.service.db.os.report.interpreter.distributedby.process.ProcessDistributedByInterpreterFacadeOS;
import io.camunda.optimize.service.db.os.report.interpreter.view.process.ProcessViewInterpreterFacadeOS;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.OpenSearchConfiguration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.aggregations.Aggregate;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.aggregations.StringTermsBucket;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchResponse;

@ExtendWith(MockitoExtension.class)
class ProcessGroupByProcessDefinitionVersionInterpreterOSTest {

  @Mock private ConfigurationService configurationService;
  @Mock private OpenSearchConfiguration openSearchConfiguration;
  @Mock private ProcessDistributedByInterpreterFacadeOS distributedByInterpreter;
  @Mock private ProcessViewInterpreterFacadeOS viewInterpreter;

  @SuppressWarnings("rawtypes")
  @Mock
  private ExecutionContext context;

  private ProcessGroupByProcessDefinitionVersionInterpreterOS underTest;

  @BeforeEach
  void setUp() {
    underTest =
        new ProcessGroupByProcessDefinitionVersionInterpreterOS(
            configurationService, distributedByInterpreter, viewInterpreter);
  }

  @Test
  void shouldReturnOnlyProcessDefinitionVersionConstantFromSupportedGroupBys() {
    assertThat(underTest.getSupportedGroupBys())
        .containsExactly(PROCESS_GROUP_BY_PROCESS_DEFINITION_VERSION);
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldAggregateBaselineCountsOnVersionFieldForPercentageView() {
    final ProcessReportDataDto reportData = mock(ProcessReportDataDto.class);
    when(reportData.getViewProperties()).thenReturn(List.of(ViewProperty.PERCENTAGE));
    when(context.getReportData()).thenReturn(reportData);

    assertThat(underTest.getBaselineCountAggregationField(context))
        .contains(PROCESS_DEFINITION_VERSION);
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldNotProvideBaselineCountFieldForNonPercentageView() {
    final ProcessReportDataDto reportData = mock(ProcessReportDataDto.class);
    when(reportData.getViewProperties()).thenReturn(List.of(ViewProperty.FREQUENCY));
    when(context.getReportData()).thenReturn(reportData);

    assertThat(underTest.getBaselineCountAggregationField(context)).isEmpty();
  }

  @Test
  void shouldBuildTermsAggregationOnVersionFieldSortedByKeyAscending() {
    when(configurationService.getOpenSearchConfiguration()).thenReturn(openSearchConfiguration);
    when(openSearchConfiguration.getAggregationBucketLimit()).thenReturn(10);
    when(distributedByInterpreter.createAggregations(any(), any())).thenReturn(Map.of());

    final Map<String, Aggregation> result = underTest.createAggregation(mock(Query.class), context);
    final Aggregation aggregation = result.get("processDefinitionVersionAgg");

    assertThat(aggregation._kind()).isEqualTo(Aggregation.Kind.Terms);
    assertThat(aggregation.terms().field()).isEqualTo(PROCESS_DEFINITION_VERSION);
    assertThat(aggregation.terms().order()).containsExactly(Map.of("_key", SortOrder.Asc));
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldMapMultipleBucketsToGroupByResults() {
    final SearchResponse<RawResult> response = mock(SearchResponse.class);
    when(response.aggregations())
        .thenReturn(Map.of("processDefinitionVersionAgg", stringTermsAggregate("1", "2", "3")));
    when(distributedByInterpreter.retrieveResult(any(), any(), any())).thenReturn(List.of());
    when(distributedByInterpreter.isKeyOfNumericType(any())).thenReturn(false);

    final CompositeCommandResult result =
        new CompositeCommandResult(new ProcessReportDataDto(), ViewProperty.FREQUENCY);
    underTest.addQueryResult(result, response, context);

    assertThat(result.getGroups())
        .extracting(CompositeCommandResult.GroupByResult::getKey)
        .containsExactly("1", "2", "3");
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldReturnEmptyGroupsWhenNoBuckets() {
    final SearchResponse<RawResult> response = mock(SearchResponse.class);
    when(response.aggregations())
        .thenReturn(Map.of("processDefinitionVersionAgg", stringTermsAggregate()));
    when(distributedByInterpreter.isKeyOfNumericType(any())).thenReturn(false);

    final CompositeCommandResult result =
        new CompositeCommandResult(new ProcessReportDataDto(), ViewProperty.FREQUENCY);
    underTest.addQueryResult(result, response, context);

    assertThat(result.getGroups()).isEmpty();
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldReturnSingleGroupResultForOneBucket() {
    final SearchResponse<RawResult> response = mock(SearchResponse.class);
    when(response.aggregations())
        .thenReturn(Map.of("processDefinitionVersionAgg", stringTermsAggregate("42")));
    when(distributedByInterpreter.retrieveResult(any(), any(), any())).thenReturn(List.of());
    when(distributedByInterpreter.isKeyOfNumericType(any())).thenReturn(false);

    final CompositeCommandResult result =
        new CompositeCommandResult(new ProcessReportDataDto(), ViewProperty.FREQUENCY);
    underTest.addQueryResult(result, response, context);

    assertThat(result.getGroups()).hasSize(1);
    assertThat(result.getGroups().get(0).getKey()).isEqualTo("42");
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldSetGroupByKeyOfNumericTypeToTrue() {
    final SearchResponse<RawResult> response = mock(SearchResponse.class);
    when(response.aggregations())
        .thenReturn(Map.of("processDefinitionVersionAgg", stringTermsAggregate()));
    when(distributedByInterpreter.isKeyOfNumericType(any())).thenReturn(false);

    final CompositeCommandResult result =
        new CompositeCommandResult(new ProcessReportDataDto(), ViewProperty.FREQUENCY);
    underTest.addQueryResult(result, response, context);

    assertThat(result.isGroupByKeyOfNumericType()).isTrue();
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

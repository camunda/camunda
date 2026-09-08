/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.interpreter.groupby.process;

import static io.camunda.optimize.service.db.DatabaseConstants.AGGREGATION_FIELD_KEY;
import static io.camunda.optimize.service.db.DatabaseConstants.PROCESS_DEFINITION_KEY_AGGREGATION;
import static io.camunda.optimize.service.db.report.plan.process.ProcessGroupBy.PROCESS_GROUP_BY_PROCESS_DEFINITION_KEY;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.PROCESS_DEFINITION_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.optimize.dto.optimize.DefinitionType;
import io.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import io.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.DefinitionService;
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
import java.util.Optional;
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
class ProcessGroupByProcessDefinitionKeyInterpreterOSTest {

  @Mock private ConfigurationService configurationService;
  @Mock private OpenSearchConfiguration openSearchConfiguration;
  @Mock private ProcessDistributedByInterpreterFacadeOS distributedByInterpreter;
  @Mock private ProcessViewInterpreterFacadeOS viewInterpreter;
  @Mock private DefinitionService definitionService;

  @SuppressWarnings("rawtypes")
  @Mock
  private ExecutionContext context;

  private ProcessGroupByProcessDefinitionKeyInterpreterOS underTest;

  @BeforeEach
  void setUp() {
    underTest =
        new ProcessGroupByProcessDefinitionKeyInterpreterOS(
            configurationService, distributedByInterpreter, viewInterpreter, definitionService);
  }

  @Test
  void shouldReturnOnlyProcessDefinitionKeyConstantFromSupportedGroupBys() {
    assertThat(underTest.getSupportedGroupBys())
        .containsExactly(PROCESS_GROUP_BY_PROCESS_DEFINITION_KEY);
  }

  @Test
  void shouldBuildTermsAggregationOnKeyFieldSortedByKeyAscending() {
    when(configurationService.getOpenSearchConfiguration()).thenReturn(openSearchConfiguration);
    when(openSearchConfiguration.getAggregationBucketLimit()).thenReturn(10);
    when(distributedByInterpreter.createAggregations(any(), any())).thenReturn(Map.of());

    final Map<String, Aggregation> result = underTest.createAggregation(mock(Query.class), context);
    final Aggregation aggregation = result.get(PROCESS_DEFINITION_KEY_AGGREGATION);

    assertThat(aggregation._kind()).isEqualTo(Aggregation.Kind.Terms);
    assertThat(aggregation.terms().field()).isEqualTo(PROCESS_DEFINITION_KEY);
    assertThat(aggregation.terms().order())
        .containsExactly(Map.of(AGGREGATION_FIELD_KEY, SortOrder.Asc));
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldMapMultipleBucketsToGroupByResults() {
    final SearchResponse<RawResult> response = mock(SearchResponse.class);
    when(response.aggregations())
        .thenReturn(
            Map.of(
                PROCESS_DEFINITION_KEY_AGGREGATION,
                stringTermsAggregate("invoice-process", "order-process", "payment-process")));
    when(distributedByInterpreter.retrieveResult(any(), any(), any())).thenReturn(List.of());
    when(distributedByInterpreter.isKeyOfNumericType(any())).thenReturn(false);

    final CompositeCommandResult result =
        new CompositeCommandResult(new ProcessReportDataDto(), ViewProperty.FREQUENCY);
    givenReportIsNotBusinessValueReport();
    underTest.addQueryResult(result, response, context);

    assertThat(result.getGroups())
        .extracting(CompositeCommandResult.GroupByResult::getKey)
        .containsExactly("invoice-process", "order-process", "payment-process");
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldReturnEmptyGroupsWhenNoBuckets() {
    final SearchResponse<RawResult> response = mock(SearchResponse.class);
    when(response.aggregations())
        .thenReturn(Map.of(PROCESS_DEFINITION_KEY_AGGREGATION, stringTermsAggregate()));
    when(distributedByInterpreter.isKeyOfNumericType(any())).thenReturn(false);

    final CompositeCommandResult result =
        new CompositeCommandResult(new ProcessReportDataDto(), ViewProperty.FREQUENCY);
    givenReportIsNotBusinessValueReport();
    underTest.addQueryResult(result, response, context);

    assertThat(result.getGroups()).isEmpty();
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldReturnSingleGroupResultForOneBucket() {
    final SearchResponse<RawResult> response = mock(SearchResponse.class);
    when(response.aggregations())
        .thenReturn(
            Map.of(PROCESS_DEFINITION_KEY_AGGREGATION, stringTermsAggregate("single-process")));
    when(distributedByInterpreter.retrieveResult(any(), any(), any())).thenReturn(List.of());
    when(distributedByInterpreter.isKeyOfNumericType(any())).thenReturn(false);

    final CompositeCommandResult result =
        new CompositeCommandResult(new ProcessReportDataDto(), ViewProperty.FREQUENCY);
    givenReportIsNotBusinessValueReport();
    underTest.addQueryResult(result, response, context);

    assertThat(result.getGroups()).hasSize(1);
    assertThat(result.getGroups().get(0).getKey()).isEqualTo("single-process");
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldLabelGroupsWithDefinitionNameForBusinessValueReports() {
    // given
    final SearchResponse<RawResult> response = mock(SearchResponse.class);
    when(response.aggregations())
        .thenReturn(
            Map.of(
                PROCESS_DEFINITION_KEY_AGGREGATION,
                stringTermsAggregate("order-fulfilment-v2", "invoice-process")));
    when(distributedByInterpreter.retrieveResult(any(), any(), any())).thenReturn(List.of());
    when(distributedByInterpreter.isKeyOfNumericType(any())).thenReturn(false);
    when(definitionService.getLatestCachedDefinitionOnAnyTenant(
            DefinitionType.PROCESS, "order-fulfilment-v2"))
        .thenReturn(Optional.of(definitionWithName("Order fulfilment")));
    when(definitionService.getLatestCachedDefinitionOnAnyTenant(
            DefinitionType.PROCESS, "invoice-process"))
        .thenReturn(Optional.of(definitionWithName("Invoice approval")));
    givenReportIsBusinessValueReport();

    // when
    final CompositeCommandResult result =
        new CompositeCommandResult(new ProcessReportDataDto(), ViewProperty.FREQUENCY);
    underTest.addQueryResult(result, response, context);

    // then the bars are labelled with the process names while the keys stay untouched
    assertThat(result.getGroups())
        .extracting(
            CompositeCommandResult.GroupByResult::getKey,
            CompositeCommandResult.GroupByResult::getLabel)
        .containsExactly(
            tuple("order-fulfilment-v2", "Order fulfilment"),
            tuple("invoice-process", "Invoice approval"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldFallBackToProcessIdWhenNameIsUnresolvable() {
    // given a business value report whose definition is not in the cache
    final SearchResponse<RawResult> response = mock(SearchResponse.class);
    when(response.aggregations())
        .thenReturn(
            Map.of(PROCESS_DEFINITION_KEY_AGGREGATION, stringTermsAggregate("orphan-process")));
    when(distributedByInterpreter.retrieveResult(any(), any(), any())).thenReturn(List.of());
    when(distributedByInterpreter.isKeyOfNumericType(any())).thenReturn(false);
    when(definitionService.getLatestCachedDefinitionOnAnyTenant(
            DefinitionType.PROCESS, "orphan-process"))
        .thenReturn(Optional.empty());
    givenReportIsBusinessValueReport();

    // when
    final CompositeCommandResult result =
        new CompositeCommandResult(new ProcessReportDataDto(), ViewProperty.FREQUENCY);
    underTest.addQueryResult(result, response, context);

    // then no label is ever blank
    assertThat(result.getGroups().get(0).getLabel()).isEqualTo("orphan-process");
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldKeepProcessIdAsLabelForRegularReports() {
    // given a user-built report, not one Optimize generated for a system dashboard
    final SearchResponse<RawResult> response = mock(SearchResponse.class);
    when(response.aggregations())
        .thenReturn(
            Map.of(
                PROCESS_DEFINITION_KEY_AGGREGATION, stringTermsAggregate("order-fulfilment-v2")));
    when(distributedByInterpreter.retrieveResult(any(), any(), any())).thenReturn(List.of());
    when(distributedByInterpreter.isKeyOfNumericType(any())).thenReturn(false);
    givenReportIsNotBusinessValueReport();

    // when
    final CompositeCommandResult result =
        new CompositeCommandResult(new ProcessReportDataDto(), ViewProperty.FREQUENCY);
    underTest.addQueryResult(result, response, context);

    // then the label stays the process id and no definition lookup happens
    assertThat(result.getGroups().get(0).getLabel()).isEqualTo("order-fulfilment-v2");
    verifyNoInteractions(definitionService);
  }

  @SuppressWarnings("unchecked")
  private void givenReportIsBusinessValueReport() {
    final ProcessReportDataDto reportData = new ProcessReportDataDto();
    reportData.setBusinessValueReport(true);
    when(context.getReportData()).thenReturn(reportData);
  }

  @SuppressWarnings("unchecked")
  private void givenReportIsNotBusinessValueReport() {
    when(context.getReportData()).thenReturn(new ProcessReportDataDto());
  }

  private static ProcessDefinitionOptimizeDto definitionWithName(final String name) {
    return ProcessDefinitionOptimizeDto.builder().key("ignored").version("1").name(name).build();
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

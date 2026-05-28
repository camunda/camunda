/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.interpreter.groupby.process;

import static io.camunda.optimize.service.db.report.plan.process.ProcessGroupBy.PROCESS_GROUP_BY_PROCESS_DEFINITION_KEY;
import static io.camunda.optimize.service.db.report.plan.process.ProcessGroupBy.PROCESS_GROUP_BY_PROCESS_DEFINITION_VERSION;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.PROCESS_DEFINITION_VERSION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch.core.search.ResponseBody;
import io.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.db.es.report.interpreter.distributedby.process.ProcessDistributedByInterpreterFacadeES;
import io.camunda.optimize.service.db.es.report.interpreter.view.process.ProcessViewInterpreterFacadeES;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.ElasticSearchConfiguration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProcessGroupByProcessDefinitionVersionInterpreterESTest {

  @Mock private ConfigurationService configurationService;
  @Mock private ElasticSearchConfiguration elasticSearchConfiguration;
  @Mock private ProcessDistributedByInterpreterFacadeES distributedByInterpreter;
  @Mock private ProcessViewInterpreterFacadeES viewInterpreter;

  @SuppressWarnings("rawtypes")
  @Mock
  private ExecutionContext context;

  private ProcessGroupByProcessDefinitionVersionInterpreterES underTest;

  @BeforeEach
  void setUp() {
    underTest =
        new ProcessGroupByProcessDefinitionVersionInterpreterES(
            configurationService, distributedByInterpreter, viewInterpreter);
  }

  @Test
  void shouldReturnOnlyProcessDefinitionVersionConstantFromSupportedGroupBys() {
    assertThat(underTest.getSupportedGroupBys())
        .containsExactly(PROCESS_GROUP_BY_PROCESS_DEFINITION_VERSION);
  }

  @Test
  void shouldNotIncludeKeyConstantInSupportedGroupBys() {
    assertThat(underTest.getSupportedGroupBys())
        .doesNotContain(PROCESS_GROUP_BY_PROCESS_DEFINITION_KEY);
  }

  @Test
  void shouldUseTermsAggregationTypeForVersionGroupBy() {
    when(configurationService.getElasticSearchConfiguration())
        .thenReturn(elasticSearchConfiguration);
    when(elasticSearchConfiguration.getAggregationBucketLimit()).thenReturn(10);
    when(distributedByInterpreter.createAggregations(any(), any())).thenReturn(Map.of());

    final Map<String, Aggregation.Builder.ContainerBuilder> result =
        underTest.createAggregation(mock(BoolQuery.class), context);
    final Aggregation aggregation = result.get("processDefinitionVersionAgg").build();

    assertThat(aggregation._kind()).isEqualTo(Aggregation.Kind.Terms);
  }

  @Test
  void shouldTargetProcessDefinitionVersionFieldInAggregation() {
    when(configurationService.getElasticSearchConfiguration())
        .thenReturn(elasticSearchConfiguration);
    when(elasticSearchConfiguration.getAggregationBucketLimit()).thenReturn(10);
    when(distributedByInterpreter.createAggregations(any(), any())).thenReturn(Map.of());

    final Map<String, Aggregation.Builder.ContainerBuilder> result =
        underTest.createAggregation(mock(BoolQuery.class), context);
    final Aggregation aggregation = result.get("processDefinitionVersionAgg").build();

    assertThat(aggregation.terms().field()).isEqualTo(PROCESS_DEFINITION_VERSION);
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldMapMultipleBucketsToGroupByResults() {
    final Aggregate aggregate =
        Aggregate.of(
            a ->
                a.sterms(
                    st ->
                        st.sumOtherDocCount(0L)
                            .docCountErrorUpperBound(0L)
                            .buckets(
                                b ->
                                    b.array(
                                        List.of(
                                            StringTermsBucket.of(
                                                sb ->
                                                    sb.key(FieldValue.of("1"))
                                                        .docCount(5L)
                                                        .aggregations(Map.of())),
                                            StringTermsBucket.of(
                                                sb ->
                                                    sb.key(FieldValue.of("2"))
                                                        .docCount(3L)
                                                        .aggregations(Map.of())),
                                            StringTermsBucket.of(
                                                sb ->
                                                    sb.key(FieldValue.of("3"))
                                                        .docCount(1L)
                                                        .aggregations(Map.of())))))));
    final ResponseBody<?> response = mock(ResponseBody.class);
    when(response.aggregations()).thenReturn(Map.of("processDefinitionVersionAgg", aggregate));
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
    final Aggregate aggregate =
        Aggregate.of(
            a ->
                a.sterms(
                    st ->
                        st.sumOtherDocCount(0L)
                            .docCountErrorUpperBound(0L)
                            .buckets(b -> b.array(List.of()))));
    final ResponseBody<?> response = mock(ResponseBody.class);
    when(response.aggregations()).thenReturn(Map.of("processDefinitionVersionAgg", aggregate));
    when(distributedByInterpreter.isKeyOfNumericType(any())).thenReturn(false);

    final CompositeCommandResult result =
        new CompositeCommandResult(new ProcessReportDataDto(), ViewProperty.FREQUENCY);
    underTest.addQueryResult(result, response, context);

    assertThat(result.getGroups()).isEmpty();
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldReturnSingleGroupResultForOneBucket() {
    final Aggregate aggregate =
        Aggregate.of(
            a ->
                a.sterms(
                    st ->
                        st.sumOtherDocCount(0L)
                            .docCountErrorUpperBound(0L)
                            .buckets(
                                b ->
                                    b.array(
                                        List.of(
                                            StringTermsBucket.of(
                                                sb ->
                                                    sb.key(FieldValue.of("42"))
                                                        .docCount(1L)
                                                        .aggregations(Map.of())))))));
    final ResponseBody<?> response = mock(ResponseBody.class);
    when(response.aggregations()).thenReturn(Map.of("processDefinitionVersionAgg", aggregate));
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
    final Aggregate aggregate =
        Aggregate.of(
            a ->
                a.sterms(
                    st ->
                        st.sumOtherDocCount(0L)
                            .docCountErrorUpperBound(0L)
                            .buckets(b -> b.array(List.of()))));
    final ResponseBody<?> response = mock(ResponseBody.class);
    when(response.aggregations()).thenReturn(Map.of("processDefinitionVersionAgg", aggregate));
    when(distributedByInterpreter.isKeyOfNumericType(any())).thenReturn(false);

    final CompositeCommandResult result =
        new CompositeCommandResult(new ProcessReportDataDto(), ViewProperty.FREQUENCY);
    underTest.addQueryResult(result, response, context);

    assertThat(result.isGroupByKeyOfNumericType()).isTrue();
  }
}

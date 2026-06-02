/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.interpreter.groupby.process;

import static io.camunda.optimize.service.db.report.plan.process.ProcessGroupBy.PROCESS_GROUP_BY_PROCESS_DEFINITION_VERSION;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.PROCESS_DEFINITION_VERSION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOrder;
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
import java.util.Arrays;
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
  void shouldBuildTermsAggregationOnVersionFieldSortedByKeyAscending() {
    when(configurationService.getElasticSearchConfiguration())
        .thenReturn(elasticSearchConfiguration);
    when(distributedByInterpreter.createAggregations(any(), any())).thenReturn(Map.of());

    final Map<String, Aggregation.Builder.ContainerBuilder> result =
        underTest.createAggregation(mock(BoolQuery.class), context);
    final Aggregation aggregation = result.get("processDefinitionVersionAgg").build();

    assertThat(aggregation._kind()).isEqualTo(Aggregation.Kind.Terms);
    assertThat(aggregation.terms().field()).isEqualTo(PROCESS_DEFINITION_VERSION);
    assertThat(aggregation.terms().order())
        .singleElement()
        .satisfies(
            order -> {
              assertThat(order.name()).isEqualTo("_key");
              assertThat(order.value()).isEqualTo(SortOrder.Asc);
            });
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldMapMultipleBucketsToGroupByResults() {
    final ResponseBody<?> response = mock(ResponseBody.class);
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
    final ResponseBody<?> response = mock(ResponseBody.class);
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
    final ResponseBody<?> response = mock(ResponseBody.class);
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
    final ResponseBody<?> response = mock(ResponseBody.class);
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
                                                        sb.key(FieldValue.of(key))
                                                            .docCount(1L)
                                                            .aggregations(Map.of())))
                                        .toList()))));
  }
}

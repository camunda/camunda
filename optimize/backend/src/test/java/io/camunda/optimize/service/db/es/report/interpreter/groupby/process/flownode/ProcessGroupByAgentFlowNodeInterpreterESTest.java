/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.interpreter.groupby.process.flownode;

import static io.camunda.optimize.service.db.report.plan.process.ProcessGroupBy.PROCESS_GROUP_BY_AGENT_FLOW_NODE;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.AGENT_INSTANCES;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.AGENT_INSTANCE_FLOW_NODE_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
import io.camunda.optimize.service.db.report.groupby.flownode.ProcessGroupByFlowNodeInterpreterHelper;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.ElasticSearchConfiguration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProcessGroupByAgentFlowNodeInterpreterESTest {

  private static final String AGENT_INSTANCES_AGG = "agentInstances";
  private static final String BY_FLOW_NODE_ID_AGG = "byFlowNodeId";

  @Mock private ConfigurationService configurationService;
  @Mock private ElasticSearchConfiguration elasticSearchConfiguration;
  @Mock private ProcessGroupByFlowNodeInterpreterHelper helper;
  @Mock private ProcessDistributedByInterpreterFacadeES distributedByInterpreter;
  @Mock private ProcessViewInterpreterFacadeES viewInterpreter;

  @SuppressWarnings("rawtypes")
  @Mock
  private ExecutionContext context;

  private ProcessGroupByAgentFlowNodeInterpreterES underTest;

  @BeforeEach
  void setUp() {
    underTest =
        new ProcessGroupByAgentFlowNodeInterpreterES(
            configurationService, helper, distributedByInterpreter, viewInterpreter);
  }

  @Test
  void shouldReturnOnlyAgentFlowNodeConstantFromSupportedGroupBys() {
    // when / then
    assertThat(underTest.getSupportedGroupBys()).containsExactly(PROCESS_GROUP_BY_AGENT_FLOW_NODE);
  }

  @Test
  void shouldBuildNestedTermsAggregationOverAgentInstancesFlowNodeId() {
    // given
    when(configurationService.getElasticSearchConfiguration())
        .thenReturn(elasticSearchConfiguration);
    when(distributedByInterpreter.createAggregations(any(), any())).thenReturn(Map.of());

    // when
    final Map<String, Aggregation.Builder.ContainerBuilder> result =
        underTest.createAggregation(mock(BoolQuery.class), context);
    final Aggregation aggregation = result.get(AGENT_INSTANCES_AGG).build();

    // then the outer aggregation is nested on the agentInstances path
    assertThat(aggregation._kind()).isEqualTo(Aggregation.Kind.Nested);
    assertThat(aggregation.nested().path()).isEqualTo(AGENT_INSTANCES);

    // and the inner terms aggregation buckets by flowNodeId within that nested path
    final Aggregation byFlowNodeId = aggregation.aggregations().get(BY_FLOW_NODE_ID_AGG);
    assertThat(byFlowNodeId._kind()).isEqualTo(Aggregation.Kind.Terms);
    assertThat(byFlowNodeId.terms().field())
        .isEqualTo(AGENT_INSTANCES + "." + AGENT_INSTANCE_FLOW_NODE_ID);
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldExtractNestedFlowNodeBucketsAndDelegateMappingToHelper() {
    // given a nested sterms aggregation with two flow-node buckets, and a helper that maps them
    final List<CompositeCommandResult.GroupByResult> mappedGroups =
        List.of(
            CompositeCommandResult.GroupByResult.createGroupByResult("task-a", "Task A", List.of()),
            CompositeCommandResult.GroupByResult.createGroupByResult(
                "task-b", "Task B", List.of()));
    when(helper.mapFlowNodeBucketsToGroupByResults(any(), any(), any(), any(), any()))
        .thenReturn(mappedGroups);
    when(distributedByInterpreter.createEmptyResult(any())).thenReturn(List.of());

    final ResponseBody<?> response = mock(ResponseBody.class);
    when(response.aggregations())
        .thenReturn(Map.of(AGENT_INSTANCES_AGG, nestedStringTermsAggregate("task-a", "task-b")));

    // when
    final CompositeCommandResult result =
        new CompositeCommandResult(new ProcessReportDataDto(), ViewProperty.TOOL_CALLS);
    underTest.addQueryResult(result, response, context);

    // then the mapping is delegated to the shared helper and its result is set on the command
    // result
    assertThat(result.getGroups()).isSameAs(mappedGroups);

    // and the buckets located under the nested path are handed over with a key extractor that reads
    // the string term key
    final ArgumentCaptor<List<StringTermsBucket>> bucketsCaptor =
        ArgumentCaptor.forClass(List.class);
    final ArgumentCaptor<Function<StringTermsBucket, String>> keyExtractorCaptor =
        ArgumentCaptor.forClass(Function.class);
    verify(helper)
        .mapFlowNodeBucketsToGroupByResults(
            bucketsCaptor.capture(), keyExtractorCaptor.capture(), any(), any(), any());
    assertThat(bucketsCaptor.getValue())
        .extracting(keyExtractorCaptor.getValue()::apply)
        .containsExactly("task-a", "task-b");
  }

  @Test
  void shouldNotInvokeHelperWhenNestedAggregationKeyIsAbsent() {
    // given a non-empty aggregation map that does not contain the nested agentInstances key
    final ResponseBody<?> response = mock(ResponseBody.class);
    when(response.aggregations())
        .thenReturn(Map.of("someOtherAgg", Aggregate.of(a -> a.sum(s -> s.value(1.0)))));

    // when
    final CompositeCommandResult result =
        new CompositeCommandResult(new ProcessReportDataDto(), ViewProperty.TOOL_CALLS);
    underTest.addQueryResult(result, response, context);

    // then the missing key is handled null-safely: no NPE, and the helper is never invoked
    assertThat(result.getGroups()).isEmpty();
    verify(helper, never()).mapFlowNodeBucketsToGroupByResults(any(), any(), any(), any(), any());
  }

  @Test
  void shouldLeaveGroupsEmptyWhenNoAggregationsPresent() {
    // given a response with no aggregations at all
    final ResponseBody<?> response = mock(ResponseBody.class);
    when(response.aggregations()).thenReturn(Map.of());

    // when
    final CompositeCommandResult result =
        new CompositeCommandResult(new ProcessReportDataDto(), ViewProperty.TOOL_CALLS);
    underTest.addQueryResult(result, response, context);

    // then
    assertThat(result.getGroups()).isEmpty();
  }

  private static Aggregate nestedStringTermsAggregate(final String... flowNodeIds) {
    return Aggregate.of(
        a ->
            a.nested(
                n ->
                    n.docCount(flowNodeIds.length)
                        .aggregations(BY_FLOW_NODE_ID_AGG, stringTermsAggregate(flowNodeIds))));
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

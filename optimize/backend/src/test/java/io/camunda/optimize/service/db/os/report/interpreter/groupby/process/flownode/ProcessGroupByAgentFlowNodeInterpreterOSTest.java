/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.interpreter.groupby.process.flownode;

import static io.camunda.optimize.service.db.report.plan.process.ProcessGroupBy.PROCESS_GROUP_BY_AGENT_FLOW_NODE;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.AGENT_INSTANCES;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.AGENT_INSTANCE_FLOW_NODE_ID;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_ID;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.db.os.report.interpreter.RawResult;
import io.camunda.optimize.service.db.os.report.interpreter.distributedby.process.ProcessDistributedByInterpreterFacadeOS;
import io.camunda.optimize.service.db.os.report.interpreter.view.process.ProcessViewInterpreterFacadeOS;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.groupby.flownode.ProcessGroupByFlowNodeInterpreterHelper;
import io.camunda.optimize.service.db.report.groupby.flownode.ProcessGroupByFlowNodeInterpreterHelper.AdHocSubProcessStructure;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.OpenSearchConfiguration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.client.opensearch._types.aggregations.Aggregate;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.aggregations.StringTermsBucket;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchResponse;

@ExtendWith(MockitoExtension.class)
class ProcessGroupByAgentFlowNodeInterpreterOSTest {

  private static final String AGENT_INSTANCES_AGG = "agentInstances";
  private static final String BY_FLOW_NODE_ID_AGG = "byFlowNodeId";
  private static final String FLOW_NODE_INSTANCES_AGG = "flowNodeInstances";
  private static final String BY_FLOW_NODE_INSTANCE_ID_AGG = "byFlowNodeInstanceId";

  @Mock private ConfigurationService configurationService;
  @Mock private OpenSearchConfiguration openSearchConfiguration;
  @Mock private ProcessGroupByFlowNodeInterpreterHelper helper;
  @Mock private ProcessDistributedByInterpreterFacadeOS distributedByInterpreter;
  @Mock private ProcessViewInterpreterFacadeOS viewInterpreter;

  @SuppressWarnings("rawtypes")
  @Mock
  private ExecutionContext context;

  private ProcessGroupByAgentFlowNodeInterpreterOS underTest;

  @BeforeEach
  void setUp() {
    underTest =
        new ProcessGroupByAgentFlowNodeInterpreterOS(
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
    when(configurationService.getOpenSearchConfiguration()).thenReturn(openSearchConfiguration);
    when(openSearchConfiguration.getAggregationBucketLimit()).thenReturn(10);
    when(distributedByInterpreter.createAggregations(any(), any())).thenReturn(Map.of());
    when(helper.resolveAdHocSubProcessStructure(any()))
        .thenReturn(new AdHocSubProcessStructure(Set.of(), Set.of()));

    // when
    final Map<String, Aggregation> result = underTest.createAggregation(mock(Query.class), context);
    final Aggregation aggregation = result.get(AGENT_INSTANCES_AGG);

    // then the outer aggregation is nested on the agentInstances path
    assertThat(aggregation._kind()).isEqualTo(Aggregation.Kind.Nested);
    assertThat(aggregation.nested().path()).isEqualTo(AGENT_INSTANCES);

    // and the inner terms aggregation buckets by flowNodeId within that nested path
    final Aggregation byFlowNodeId = aggregation.aggregations().get(BY_FLOW_NODE_ID_AGG);
    assertThat(byFlowNodeId._kind()).isEqualTo(Aggregation.Kind.Terms);
    assertThat(byFlowNodeId.terms().field())
        .isEqualTo(AGENT_INSTANCES + "." + AGENT_INSTANCE_FLOW_NODE_ID);

    // and no extra flow-node-instance aggregation is added when there are no ad-hoc subprocess
    // tools
    assertThat(result).doesNotContainKey(FLOW_NODE_INSTANCES_AGG);
  }

  @Test
  void shouldAlsoBuildFlowNodeInstanceAggregationWhenAdHocSubProcessToolsPresent() {
    // given a report whose model contains ad-hoc subprocess tool nodes
    when(configurationService.getOpenSearchConfiguration()).thenReturn(openSearchConfiguration);
    when(openSearchConfiguration.getAggregationBucketLimit()).thenReturn(10);
    when(distributedByInterpreter.createAggregations(any(), any())).thenReturn(Map.of());
    when(helper.resolveAdHocSubProcessStructure(any()))
        .thenReturn(new AdHocSubProcessStructure(Set.of("ahsp"), Set.of("tool-a", "tool-b")));

    // when
    final Map<String, Aggregation> result = underTest.createAggregation(mock(Query.class), context);

    // then a second nested aggregation over the flowNodeInstances path is added
    final Aggregation flowNodeInstances = result.get(FLOW_NODE_INSTANCES_AGG);
    assertThat(flowNodeInstances._kind()).isEqualTo(Aggregation.Kind.Nested);
    assertThat(flowNodeInstances.nested().path()).isEqualTo(FLOW_NODE_INSTANCES);

    final Aggregation byFlowNodeInstanceId =
        flowNodeInstances.aggregations().get(BY_FLOW_NODE_INSTANCE_ID_AGG);
    assertThat(byFlowNodeInstanceId._kind()).isEqualTo(Aggregation.Kind.Terms);
    assertThat(byFlowNodeInstanceId.terms().field())
        .isEqualTo(FLOW_NODE_INSTANCES + "." + FLOW_NODE_ID);

    // and the original agent aggregation is still present
    assertThat(result).containsKey(AGENT_INSTANCES_AGG);
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
    when(helper.mapAgentFlowNodeBucketsToGroupByResults(
            any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(mappedGroups);
    when(helper.resolveAdHocSubProcessStructure(any()))
        .thenReturn(new AdHocSubProcessStructure(Set.of(), Set.of()));
    when(distributedByInterpreter.createEmptyResult(any())).thenReturn(List.of());

    final SearchResponse<RawResult> response = mock(SearchResponse.class);
    when(response.aggregations())
        .thenReturn(Map.of(AGENT_INSTANCES_AGG, nestedStringTermsAggregate("task-a", "task-b")));

    // when
    final CompositeCommandResult result =
        new CompositeCommandResult(new ProcessReportDataDto(), ViewProperty.TOOL_CALLS);
    underTest.addQueryResult(result, response, context);

    // then the mapping is delegated to the shared helper and its result is set on the command
    // result
    assertThat(result.getGroups()).isSameAs(mappedGroups);

    // and the agent buckets located under the nested path are handed over with a key extractor that
    // reads the string term key
    final ArgumentCaptor<List<StringTermsBucket>> bucketsCaptor =
        ArgumentCaptor.forClass(List.class);
    final ArgumentCaptor<Function<StringTermsBucket, String>> keyExtractorCaptor =
        ArgumentCaptor.forClass(Function.class);
    verify(helper)
        .mapAgentFlowNodeBucketsToGroupByResults(
            bucketsCaptor.capture(),
            keyExtractorCaptor.capture(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any());
    assertThat(bucketsCaptor.getValue())
        .extracting(keyExtractorCaptor.getValue()::apply)
        .containsExactly("task-a", "task-b");
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldExtractInnerToolBucketsFromFlowNodeInstancesAggregation() {
    // given a response containing both the agent aggregation and the flow-node-instance aggregation
    when(helper.mapAgentFlowNodeBucketsToGroupByResults(
            any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(List.of());
    when(helper.resolveAdHocSubProcessStructure(any()))
        .thenReturn(new AdHocSubProcessStructure(Set.of("ahsp"), Set.of("tool-a", "tool-b")));
    when(distributedByInterpreter.createEmptyResult(any())).thenReturn(List.of());

    final SearchResponse<RawResult> response = mock(SearchResponse.class);
    when(response.aggregations())
        .thenReturn(
            Map.of(
                AGENT_INSTANCES_AGG, nestedStringTermsAggregate("ahsp"),
                FLOW_NODE_INSTANCES_AGG, nestedFlowNodeInstanceTermsAggregate("tool-a", "tool-b")));

    // when
    final CompositeCommandResult result =
        new CompositeCommandResult(new ProcessReportDataDto(), ViewProperty.TOOL_CALLS);
    underTest.addQueryResult(result, response, context);

    // then the inner-tool buckets (4th argument) are read from the flow-node-instance aggregation
    final ArgumentCaptor<List<StringTermsBucket>> innerToolBucketsCaptor =
        ArgumentCaptor.forClass(List.class);
    final ArgumentCaptor<Function<StringTermsBucket, String>> innerKeyExtractorCaptor =
        ArgumentCaptor.forClass(Function.class);
    verify(helper)
        .mapAgentFlowNodeBucketsToGroupByResults(
            any(),
            any(),
            any(),
            innerToolBucketsCaptor.capture(),
            innerKeyExtractorCaptor.capture(),
            any(),
            any(),
            any(),
            any());
    assertThat(innerToolBucketsCaptor.getValue())
        .extracting(innerKeyExtractorCaptor.getValue()::apply)
        .containsExactly("tool-a", "tool-b");
  }

  @Test
  void shouldNotInvokeHelperWhenNestedAggregationKeyIsAbsent() {
    // given a non-empty aggregation map that does not contain the nested agentInstances key
    final SearchResponse<RawResult> response = mock(SearchResponse.class);
    when(response.aggregations())
        .thenReturn(Map.of("someOtherAgg", Aggregate.of(a -> a.sum(s -> s.value(1.0)))));

    // when
    final CompositeCommandResult result =
        new CompositeCommandResult(new ProcessReportDataDto(), ViewProperty.TOOL_CALLS);
    underTest.addQueryResult(result, response, context);

    // then the missing key is handled null-safely: no NPE, and the helper is never invoked
    assertThat(result.getGroups()).isEmpty();
    verify(helper, never())
        .mapAgentFlowNodeBucketsToGroupByResults(
            any(), any(), any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  void shouldLeaveGroupsEmptyWhenNoAggregationsPresent() {
    // given a response with no aggregations at all
    final SearchResponse<RawResult> response = mock(SearchResponse.class);
    when(response.aggregations()).thenReturn(Map.of());

    // when
    final CompositeCommandResult result =
        new CompositeCommandResult(new ProcessReportDataDto(), ViewProperty.TOOL_CALLS);
    underTest.addQueryResult(result, response, context);

    // then
    assertThat(result.getGroups()).isEmpty();
  }

  private static Aggregate nestedFlowNodeInstanceTermsAggregate(final String... flowNodeIds) {
    return Aggregate.of(
        a ->
            a.nested(
                n ->
                    n.docCount(flowNodeIds.length)
                        .aggregations(
                            BY_FLOW_NODE_INSTANCE_ID_AGG, stringTermsAggregate(flowNodeIds))));
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
                                                        sb.key(key)
                                                            .docCount(1L)
                                                            .aggregations(Map.of())))
                                        .toList()))));
  }
}

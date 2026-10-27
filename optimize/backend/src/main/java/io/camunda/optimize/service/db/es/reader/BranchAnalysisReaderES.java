/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.reader;

import static io.camunda.optimize.dto.optimize.DefinitionType.PROCESS;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_ID;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;
import static io.camunda.optimize.service.util.ExceptionUtil.isInstanceIndexNotFoundException;
import static io.camunda.optimize.service.util.InstanceIndexUtil.getProcessInstanceIndexAliasName;

import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.ChildScoreMode;
import co.elastic.clients.elasticsearch._types.query_dsl.NestedQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.CountRequest;
import co.elastic.clients.elasticsearch.core.CountResponse;
import io.camunda.optimize.dto.optimize.query.analysis.BranchAnalysisRequestDto;
import io.camunda.optimize.service.DefinitionService;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.es.builders.OptimizeCountRequestBuilderES;
import io.camunda.optimize.service.db.es.filter.ProcessQueryFilterEnhancerES;
import io.camunda.optimize.service.db.es.schema.index.ProcessInstanceIndexES;
import io.camunda.optimize.service.db.filter.FilterContext;
import io.camunda.optimize.service.db.reader.BranchAnalysisReader;
import io.camunda.optimize.service.db.reader.ProcessDefinitionReader;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.DefinitionQueryUtilES;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.io.IOException;
import java.time.ZoneId;
import java.util.Set;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class BranchAnalysisReaderES extends BranchAnalysisReader {

  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(BranchAnalysisReaderES.class);

  private final OptimizeElasticsearchClient esClient;
  private final ProcessQueryFilterEnhancerES queryFilterEnhancer;
  private final ProcessDefinitionReader processDefinitionReader;

  public BranchAnalysisReaderES(
      final DefinitionService definitionService,
      final OptimizeElasticsearchClient esClient,
      final ProcessQueryFilterEnhancerES queryFilterEnhancer,
      final ProcessDefinitionReader processDefinitionReader) {
    super(definitionService);
    this.esClient = esClient;
    this.queryFilterEnhancer = queryFilterEnhancer;
    this.processDefinitionReader = processDefinitionReader;
  }

  @Override
  protected long calculateReachedEndEventFlowNodeCount(
      final String flowNodeId,
      final BranchAnalysisRequestDto request,
      final Set<String> activitiesToExclude,
      final ZoneId timezone) {
    final BoolQuery.Builder builder =
        buildBaseQuery(request, activitiesToExclude)
            .must(createMustMatchFlowNodeIdQuery(request.getGateway()))
            .must(createMustMatchFlowNodeIdQuery(flowNodeId))
            .must(createMustMatchFlowNodeIdQuery(request.getEnd()));

    return executeQuery(request, builder, timezone);
  }

  @Override
  protected long calculateFlowNodeCount(
      final String flowNodeId,
      final BranchAnalysisRequestDto request,
      final Set<String> activitiesToExclude,
      final ZoneId timezone) {
    final BoolQuery.Builder builder =
        buildBaseQuery(request, activitiesToExclude)
            .must(createMustMatchFlowNodeIdQuery(request.getGateway()))
            .must(createMustMatchFlowNodeIdQuery(flowNodeId));

    return executeQuery(request, builder, timezone);
  }

  private BoolQuery.Builder buildBaseQuery(
      final BranchAnalysisRequestDto request, final Set<String> activitiesToExclude) {
    final BoolQuery.Builder query =
        DefinitionQueryUtilES.createDefinitionQuery(
            request.getProcessDefinitionKey(),
            request.getProcessDefinitionVersions(),
            request.getTenantIds(),
            new ProcessInstanceIndexES(request.getProcessDefinitionKey()),
            processDefinitionReader::getLatestVersionToKey);
    excludeFlowNodes(activitiesToExclude, query);
    return query;
  }

  private void excludeFlowNodes(
      final Set<String> flowNodeIdsToExclude, final BoolQuery.Builder query) {
    for (final String excludeFlowNodeId : flowNodeIdsToExclude) {
      query.mustNot(createMustMatchFlowNodeIdQuery(excludeFlowNodeId));
    }
  }

  private Query createMustMatchFlowNodeIdQuery(final String flowNodeId) {
    return Query.of(
        qu ->
            qu.nested(
                NestedQuery.of(
                    b ->
                        b.path(FLOW_NODE_INSTANCES)
                            .query(
                                q ->
                                    q.term(
                                        t ->
                                            t.field(FLOW_NODE_INSTANCES + "." + FLOW_NODE_ID)
                                                .value(FieldValue.of(flowNodeId))))
                            .scoreMode(ChildScoreMode.None))));
  }

  private long executeQuery(
      final BranchAnalysisRequestDto request, final BoolQuery.Builder bool, final ZoneId timezone) {
    queryFilterEnhancer.addFilterToQuery(
        bool, request.getFilter(), FilterContext.builder().timezone(timezone).build());
    final CountRequest countRequest =
        OptimizeCountRequestBuilderES.of(
            b ->
                b.optimizeIndex(
                        esClient,
                        getProcessInstanceIndexAliasName(request.getProcessDefinitionKey()))
                    .query(q -> q.bool(bool.build())));

    try {
      final CountResponse countResponse = esClient.count(countRequest);
      return countResponse.count();
    } catch (final IOException e) {
      final String reason =
          String.format(
              "Was not able to perform branch analysis on process definition with key [%s] and versions [%s}]",
              request.getProcessDefinitionKey(), request.getProcessDefinitionVersions());
      LOG.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    } catch (final ElasticsearchException e) {
      if (isInstanceIndexNotFoundException(PROCESS, e)) {
        LOG.info(
            "Was not able to perform branch analysis because the required instance index {} does not "
                + "exist. Returning 0 instead.",
            getProcessInstanceIndexAliasName(request.getProcessDefinitionKey()));
        return 0L;
      }
      throw e;
    }
  }
}

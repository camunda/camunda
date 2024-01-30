/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.store.opensearch;

import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.templates.FlowNodeInstanceTemplate;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.store.FlowNodeStore;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.store.opensearch.dsl.RequestDSL;
import io.camunda.operate.util.ThreadUtil;
import org.opensearch.client.opensearch.core.search.Hit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static io.camunda.operate.schema.templates.ListViewTemplate.ACTIVITIES_JOIN_RELATION;
import static io.camunda.operate.schema.templates.ListViewTemplate.JOIN_RELATION;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.*;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.searchRequestBuilder;

@Conditional(OpensearchCondition.class)
@Component
public class OpensearchFlowNodeStore implements FlowNodeStore {

  @Autowired
  private ListViewTemplate listViewTemplate;

  @Autowired
  private FlowNodeInstanceTemplate flowNodeInstanceTemplate;

  @Autowired
  private RichOpenSearchClient richOpenSearchClient;

  @Autowired
  private OperateProperties operateProperties;

  @Override
  public String getFlowNodeIdByFlowNodeInstanceId(String flowNodeInstanceId) {
    record Result(String activityId){}
    final RequestDSL.QueryType queryType = operateProperties.getImporter().isReadArchivedParents() ? RequestDSL.QueryType.ALL : RequestDSL.QueryType.ONLY_RUNTIME;
    var searchRequestBuilder = searchRequestBuilder(listViewTemplate, queryType)
      .query(withTenantCheck(
        and(
          term(JOIN_RELATION, ACTIVITIES_JOIN_RELATION),
          term(ListViewTemplate.ID, flowNodeInstanceId)
        )
      ));

    return richOpenSearchClient.doc().searchUnique(searchRequestBuilder, Result.class, flowNodeInstanceId)
      .activityId();
  }

  @Override
  public Map<String, String> getFlowNodeIdsForFlowNodeInstances(Set<String> flowNodeInstanceIds) {
    record Result(String flowNodeId){}
    final Map<String, String> flowNodeIdsMap = new HashMap<>();
    var searchRequestBuilder = searchRequestBuilder(flowNodeInstanceTemplate, RequestDSL.QueryType.ONLY_RUNTIME)
        .query(withTenantCheck(stringTerms(FlowNodeInstanceTemplate.ID, flowNodeInstanceIds)));
    final Consumer<List<Hit<Result>>> hitsConsumer = hits -> hits.forEach(h -> flowNodeIdsMap.put(h.id(), h.source().flowNodeId()));

    richOpenSearchClient.doc().scrollWith(searchRequestBuilder, Result.class, hitsConsumer);

    return flowNodeIdsMap;
  }

  @Override
  public String findParentTreePathFor(long parentFlowNodeInstanceKey) {
    return findParentTreePath(parentFlowNodeInstanceKey, 0);
  }

  private String findParentTreePath(final long parentFlowNodeInstanceKey, int attemptCount) {
    record Result(String treePath){}
    final RequestDSL.QueryType queryType = operateProperties.getImporter().isReadArchivedParents() ?
      RequestDSL.QueryType.ALL :
      RequestDSL.QueryType.ONLY_RUNTIME;
    var searchRequestBuilder = searchRequestBuilder(flowNodeInstanceTemplate, queryType)
        .query(withTenantCheck(term(FlowNodeInstanceTemplate.KEY, parentFlowNodeInstanceKey)));

    final List<Hit<Result>> hits = richOpenSearchClient.doc().search(searchRequestBuilder, Result.class).hits().hits();

    if (hits.size() > 0) {
      return hits.get(0).source().treePath();
    } else if (attemptCount < 1){
      //retry for the case, when ELS has not yet refreshed the indices
      ThreadUtil.sleepFor(2000L);
      return findParentTreePath(parentFlowNodeInstanceKey, attemptCount + 1);
    } else {
      return null;
    }
  }
}

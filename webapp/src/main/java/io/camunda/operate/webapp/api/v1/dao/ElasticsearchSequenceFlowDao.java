/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.api.v1.dao;

import static io.camunda.operate.util.ElasticsearchUtil.joinWithAnd;

import io.camunda.operate.schema.templates.SequenceFlowTemplate;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.webapp.api.v1.entities.SequenceFlow;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.entities.Results;
import io.camunda.operate.webapp.api.v1.exceptions.APIException;
import io.camunda.operate.webapp.api.v1.exceptions.ServerException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component("ElasticsearchSequenceFlowDaoV1")
public class ElasticsearchSequenceFlowDao extends ElasticsearchDao<SequenceFlow> implements SequenceFlowDao {

  @Autowired
  private SequenceFlowTemplate sequenceFlowIndex;

  @Override
  protected void buildFiltering(final Query<SequenceFlow> query, final SearchSourceBuilder searchSourceBuilder) {
    final SequenceFlow filter = query.getFilter();
    List<QueryBuilder> queryBuilders = new ArrayList<>();
    if (filter != null) {
      queryBuilders.add(buildTermQuery(SequenceFlow.ID, filter.getId()));
      queryBuilders.add(buildTermQuery(SequenceFlow.ACTIVITY_ID, filter.getActivityId()));
      queryBuilders.add(buildTermQuery(SequenceFlow.PROCESS_INSTANCE_KEY, filter.getProcessInstanceKey()));
    }
    searchSourceBuilder.query(
        joinWithAnd(queryBuilders.toArray(new QueryBuilder[]{})));
  }

  @Override
  public Results<SequenceFlow> search(final Query<SequenceFlow> query) throws APIException {
    logger.debug("search {}", query);
    final SearchSourceBuilder searchSourceBuilder = buildQueryOn(
        query,
        SequenceFlow.ID,
        new SearchSourceBuilder());
    try {
      final SearchRequest searchRequest = new SearchRequest().indices(
              sequenceFlowIndex.getAlias())
          .source(searchSourceBuilder);
      final SearchResponse searchResponse = elasticsearch.search(searchRequest,
          RequestOptions.DEFAULT);
      final SearchHits searchHits = searchResponse.getHits();
      final SearchHit[] searchHitArray = searchHits.getHits();
      if (searchHitArray != null && searchHitArray.length > 0) {
        final Object[] sortValues = searchHitArray[searchHitArray.length - 1].getSortValues();
        List<SequenceFlow> sequenceFlows =
            ElasticsearchUtil.mapSearchHits(searchHitArray, this::searchHitToSequenceFlow);
        return new Results<SequenceFlow>()
            .setTotal(searchHits.getTotalHits().value)
            .setItems(sequenceFlows)
            .setSortValues(sortValues);
      } else {
        return new Results<SequenceFlow>().setTotal(searchHits.getTotalHits().value);
      }
    } catch (Exception e) {
      throw new ServerException("Error in reading sequence flows", e);
    }
  }

  protected SequenceFlow searchHitToSequenceFlow(SearchHit searchHit) {
    final Map<String,Object> searchHitAsMap = searchHit.getSourceAsMap();
    return new SequenceFlow()
        .setId((String) searchHitAsMap.get(SequenceFlowTemplate.ID))
        .setActivityId((String) searchHitAsMap.get(SequenceFlowTemplate.ACTIVITY_ID))
        .setProcessInstanceKey((Long) searchHitAsMap.get(SequenceFlowTemplate.PROCESS_INSTANCE_KEY));
  }
}

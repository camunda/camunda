/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.store.elasticsearch;

import static io.camunda.operate.util.CollectionUtil.map;
import static io.camunda.operate.util.CollectionUtil.toSafeArrayOfStrings;
import static io.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static org.elasticsearch.index.query.QueryBuilders.*;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;

import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.store.ListViewStore;
import io.camunda.operate.store.NotFoundException;
import io.camunda.operate.tenant.TenantAwareElasticsearchClient;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.webapps.schema.descriptors.template.ListViewTemplate;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(ElasticsearchCondition.class)
@Component
public class ElasticsearchListViewStore implements ListViewStore {

  @Autowired private ListViewTemplate listViewTemplate;

  @Autowired private RestHighLevelClient esClient;

  @Autowired private TenantAwareElasticsearchClient tenantAwareClient;

  @Autowired private OperateProperties operateProperties;

  @Override
  public Map<Long, String> getListViewIndicesForProcessInstances(
      final List<Long> processInstanceIds) throws IOException {
    final List<String> processInstanceIdsAsStrings = map(processInstanceIds, Object::toString);

    final SearchRequest searchRequest =
        ElasticsearchUtil.createSearchRequest(listViewTemplate, ElasticsearchUtil.QueryType.ALL);
    searchRequest
        .source()
        .query(QueryBuilders.idsQuery().addIds(toSafeArrayOfStrings(processInstanceIdsAsStrings)));

    final Map<Long, String> processInstanceId2IndexName = new HashMap<>();
    tenantAwareClient.search(
        searchRequest,
        () -> {
          ElasticsearchUtil.scrollWith(
              searchRequest,
              esClient,
              searchHits -> {
                for (final SearchHit searchHit : searchHits.getHits()) {
                  final String indexName = searchHit.getIndex();
                  final Long id = Long.valueOf(searchHit.getId());
                  processInstanceId2IndexName.put(id, indexName);
                }
              });
          return null;
        });

    if (processInstanceId2IndexName.isEmpty()) {
      throw new NotFoundException(
          String.format("Process instances %s doesn't exists.", processInstanceIds));
    }
    return processInstanceId2IndexName;
  }

  @Override
  public String findProcessInstanceTreePathFor(final long processInstanceKey) {
    final ElasticsearchUtil.QueryType queryType =
        operateProperties.getImporter().isReadArchivedParents()
            ? ElasticsearchUtil.QueryType.ALL
            : ElasticsearchUtil.QueryType.ONLY_RUNTIME;
    final SearchRequest searchRequest =
        ElasticsearchUtil.createSearchRequest(listViewTemplate, queryType)
            .source(
                new SearchSourceBuilder()
                    .query(termQuery(ListViewTemplate.KEY, processInstanceKey))
                    .fetchSource(ListViewTemplate.TREE_PATH, null));
    try {
      final SearchHits hits = tenantAwareClient.search(searchRequest).getHits();
      if (hits.getTotalHits().value > 0) {
        return (String) hits.getHits()[0].getSourceAsMap().get(ListViewTemplate.TREE_PATH);
      }
      return null;
    } catch (final IOException e) {
      final String message =
          String.format(
              "Exception occurred, while searching for process instance tree path: %s",
              e.getMessage());
      throw new OperateRuntimeException(message, e);
    }
  }

  @Override
  public List<Long> getProcessInstanceKeysWithEmptyProcessVersionFor(
      final Long processDefinitionKey) {
    final QueryBuilder queryBuilder =
        constantScoreQuery(
            joinWithAnd(
                termQuery(ListViewTemplate.PROCESS_KEY, processDefinitionKey),
                boolQuery().mustNot(existsQuery(ListViewTemplate.PROCESS_VERSION))));
    final SearchRequest searchRequest =
        new SearchRequest(listViewTemplate.getAlias())
            .source(new SearchSourceBuilder().query(queryBuilder).fetchSource(false));
    try {
      return tenantAwareClient.search(
          searchRequest,
          () -> {
            return ElasticsearchUtil.scrollKeysToList(searchRequest, esClient);
          });
    } catch (final IOException e) {
      final String message =
          String.format(
              "Exception occurred, while obtaining process instance that has empty versions: %s",
              e.getMessage());
      throw new OperateRuntimeException(message, e);
    }
  }
}

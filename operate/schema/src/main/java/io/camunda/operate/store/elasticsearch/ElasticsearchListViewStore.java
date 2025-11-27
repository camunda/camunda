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
import static org.elasticsearch.index.query.QueryBuilders.*;

import io.camunda.operate.conditions.ElasticsearchCondition;
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
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(ElasticsearchCondition.class)
@Component
public class ElasticsearchListViewStore implements ListViewStore {

  @Autowired private ListViewTemplate listViewTemplate;

  @Autowired private RestHighLevelClient esClient;

  @Autowired private TenantAwareElasticsearchClient tenantAwareClient;

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
}

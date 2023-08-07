/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.store.elasticsearch;

import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.store.ListViewStore;
import io.camunda.operate.store.NotFoundException;
import io.camunda.operate.util.ElasticsearchUtil;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.camunda.operate.util.CollectionUtil.map;
import static io.camunda.operate.util.CollectionUtil.toSafeArrayOfStrings;

@Profile("!opensearch")
@Component
public class ElasticsearchListViewStore implements ListViewStore {

  @Autowired
  private ListViewTemplate listViewTemplate;

  @Autowired
  private RestHighLevelClient esClient;

  @Override
  public Map<Long, String> getListViewIndicesForProcessInstances(List<Long> processInstanceIds) throws IOException {
    final List<String> processInstanceIdsAsStrings = map(processInstanceIds, Object::toString);

    final SearchRequest searchRequest = ElasticsearchUtil.createSearchRequest(listViewTemplate, ElasticsearchUtil.QueryType.ALL);
    searchRequest.source().query(QueryBuilders.idsQuery().addIds(toSafeArrayOfStrings(processInstanceIdsAsStrings)));

    final Map<Long,String> processInstanceId2IndexName = new HashMap<>();
    ElasticsearchUtil.scrollWith(searchRequest, esClient, searchHits -> {
      for(SearchHit searchHit: searchHits.getHits()){
        final String indexName = searchHit.getIndex();
        final Long id = Long.valueOf(searchHit.getId());
        processInstanceId2IndexName.put(id, indexName);
      }
    });

    if(processInstanceId2IndexName.isEmpty()){
      throw new NotFoundException(String.format("Process instances %s doesn't exists.", processInstanceIds));
    }
    return processInstanceId2IndexName;
  }
}

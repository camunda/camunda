/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.it.elasticsearch;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.entities.BatchOperationEntity;
import io.camunda.operate.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.operate.it.ArchiverITRepository;
import io.camunda.operate.util.CollectionUtil;
import io.camunda.operate.util.ElasticsearchUtil;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.IdsQueryBuilder;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.index.query.TermsQueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static io.camunda.operate.schema.templates.ListViewTemplate.JOIN_RELATION;
import static io.camunda.operate.schema.templates.ListViewTemplate.PROCESS_INSTANCE_JOIN_RELATION;
import static io.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static org.elasticsearch.index.query.QueryBuilders.constantScoreQuery;
import static org.elasticsearch.index.query.QueryBuilders.idsQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

@Component
@Conditional(ElasticsearchCondition.class)
public class ElasticsearchArchiverITRepository implements ArchiverITRepository {
  @Autowired
  private RestHighLevelClient esClient;

  @Autowired
  private ObjectMapper objectMapper;

  @Override
  public List<BatchOperationEntity> getBatchOperationEntities(String indexName, List<String> ids) throws IOException {
    final IdsQueryBuilder idsQ = idsQuery().addIds(CollectionUtil.toSafeArrayOfStrings(ids));

    final SearchRequest searchRequest = new SearchRequest(indexName)
      .source(new SearchSourceBuilder()
        .query(constantScoreQuery(idsQ))
        .size(100));

    final SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);

    return ElasticsearchUtil.mapSearchHits(response.getHits().getHits(), objectMapper, BatchOperationEntity.class);
  }

  @Override
  public List<ProcessInstanceForListViewEntity> getProcessInstances(String indexName, List<Long> ids) throws IOException {
    final IdsQueryBuilder idsQ = idsQuery().addIds(CollectionUtil.toSafeArrayOfStrings(ids));
    final TermQueryBuilder isProcessInstanceQuery = termQuery(JOIN_RELATION, PROCESS_INSTANCE_JOIN_RELATION);

    final SearchRequest searchRequest = new SearchRequest(indexName)
      .source(new SearchSourceBuilder()
        .query(constantScoreQuery(joinWithAnd(idsQ, isProcessInstanceQuery)))
        .size(100));

    final SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);

    return ElasticsearchUtil.mapSearchHits(response.getHits().getHits(), objectMapper, ProcessInstanceForListViewEntity.class);
  }

  @Override
  public Optional<List<Long>> getIds(String indexName, String idFieldName, List<Long> ids, boolean ignoreAbsentIndex) throws IOException {
    try {
      final TermsQueryBuilder q = termsQuery(idFieldName, CollectionUtil.toSafeArrayOfStrings(ids));
      final SearchRequest request = new SearchRequest(indexName)
        .source(new SearchSourceBuilder()
          .query(q)
          .size(100));
      return Optional.of(ElasticsearchUtil.scrollFieldToList(request, idFieldName, esClient));
    } catch (ElasticsearchStatusException ex) {
      if (!ex.getMessage().contains("index_not_found_exception") || !ignoreAbsentIndex) {
        throw ex;
      }
      return Optional.empty();
    }
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.es.reader;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.entities.BatchOperationEntity;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.schema.templates.BatchOperationTemplate;
import io.camunda.operate.webapp.rest.dto.operation.BatchOperationRequestDto;
import io.camunda.operate.webapp.security.UserService;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.SortBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.elasticsearch.client.Requests.searchRequest;
import static org.elasticsearch.index.query.QueryBuilders.constantScoreQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.search.builder.SearchSourceBuilder.searchSource;

@Component
public class BatchOperationReader {

  private static final Logger logger = LoggerFactory.getLogger(BatchOperationReader.class);

  @Autowired
  private BatchOperationTemplate batchOperationTemplate;

  @Autowired
  private UserService userService;

  @Autowired
  private RestHighLevelClient esClient;

  @Autowired
  private ObjectMapper objectMapper;

  public List<BatchOperationEntity> getBatchOperations(BatchOperationRequestDto batchOperationRequestDto) {

    SearchRequest searchRequest = createSearchRequest(batchOperationRequestDto);
    final SearchResponse searchResponse;
    try {
      searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
      List<BatchOperationEntity> batchOperationEntities = ElasticsearchUtil.mapSearchHits(searchResponse.getHits().getHits(),
          (sh) -> {
            BatchOperationEntity entity = ElasticsearchUtil.fromSearchHit(sh.getSourceAsString(), objectMapper, BatchOperationEntity.class);
            entity.setSortValues(sh.getSortValues());
            return entity;
          });
      if (batchOperationRequestDto.getSearchBefore() != null) {
        Collections.reverse(batchOperationEntities);
      }
      return batchOperationEntities;
    } catch (IOException e) {
      final String message = String.format("Exception occurred, while getting page of batch operations list: %s", e.getMessage());
      logger.error(message, e);
      throw new OperateRuntimeException(message, e);
    }

  }

  private SearchRequest createSearchRequest(BatchOperationRequestDto batchOperationRequestDto) {
    QueryBuilder queryBuilder = termQuery(BatchOperationTemplate.USERNAME, userService.getCurrentUser().getUsername());

    SortBuilder sort1,
                sort2;
    Object[] querySearchAfter;

    Object[] searchAfter = batchOperationRequestDto.getSearchAfter(objectMapper);
    Object[] searchBefore = batchOperationRequestDto.getSearchBefore(objectMapper);
    if (searchAfter != null || searchBefore == null) { //this sorting is also the default one for 1st page
      sort1 = new FieldSortBuilder(BatchOperationTemplate.END_DATE).order(SortOrder.DESC).missing("_first");
      sort2 = new FieldSortBuilder(BatchOperationTemplate.START_DATE).order(SortOrder.DESC);
      querySearchAfter = searchAfter; //may be null
    } else { //searchBefore != null
      //reverse sorting
      sort1 = new FieldSortBuilder(BatchOperationTemplate.END_DATE).order(SortOrder.ASC).missing("_last");
      sort2 = new FieldSortBuilder(BatchOperationTemplate.START_DATE).order(SortOrder.ASC);
      querySearchAfter = searchBefore;
    }

    SearchSourceBuilder sourceBuilder = searchSource()
        .query(constantScoreQuery(queryBuilder))
        .sort(sort1)
        .sort(sort2)
        .size(batchOperationRequestDto.getPageSize());
    if (querySearchAfter != null) {
      sourceBuilder.searchAfter(querySearchAfter);
    }
    return searchRequest(batchOperationTemplate.getAlias()).source(sourceBuilder);
  }

}

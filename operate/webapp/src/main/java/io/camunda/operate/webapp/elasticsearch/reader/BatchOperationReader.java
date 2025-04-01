/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.elasticsearch.reader;

import static org.elasticsearch.client.Requests.searchRequest;
import static org.elasticsearch.index.query.QueryBuilders.constantScoreQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.search.builder.SearchSourceBuilder.searchSource;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.webapp.rest.dto.operation.BatchOperationRequestDto;
import io.camunda.operate.webapp.security.UserService;
import io.camunda.webapps.schema.descriptors.template.BatchOperationTemplate;
import io.camunda.webapps.schema.entities.operation.BatchOperationEntity;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(ElasticsearchCondition.class)
@Component
public class BatchOperationReader implements io.camunda.operate.webapp.reader.BatchOperationReader {

  private static final Logger LOGGER = LoggerFactory.getLogger(BatchOperationReader.class);

  @Autowired private BatchOperationTemplate batchOperationTemplate;

  @Autowired private UserService userService;

  @Autowired private RestHighLevelClient esClient;

  @Autowired
  @Qualifier("operateObjectMapper")
  private ObjectMapper objectMapper;

  @Override
  public List<BatchOperationEntity> getBatchOperations(
      final BatchOperationRequestDto batchOperationRequestDto) {

    final SearchRequest searchRequest = createSearchRequest(batchOperationRequestDto);
    final SearchResponse searchResponse;
    try {
      searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
      final List<BatchOperationEntity> batchOperationEntities =
          ElasticsearchUtil.mapSearchHits(
              searchResponse.getHits().getHits(),
              (sh) -> {
                final BatchOperationEntity entity =
                    ElasticsearchUtil.fromSearchHit(
                        sh.getSourceAsString(), objectMapper, BatchOperationEntity.class);
                entity.setSortValues(sh.getSortValues());
                return entity;
              });
      if (batchOperationRequestDto.getSearchBefore() != null) {
        Collections.reverse(batchOperationEntities);
      }
      return batchOperationEntities;
    } catch (final IOException e) {
      final String message =
          String.format(
              "Exception occurred, while getting page of batch operations list: %s",
              e.getMessage());
      LOGGER.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  private SearchRequest createSearchRequest(
      final BatchOperationRequestDto batchOperationRequestDto) {
    final QueryBuilder queryBuilder =
        termQuery(BatchOperationTemplate.USERNAME, userService.getCurrentUser().getUsername());

    final SortBuilder sort1, sort2;
    final Object[] querySearchAfter;

    final Object[] searchAfter = batchOperationRequestDto.getSearchAfter(objectMapper);
    final Object[] searchBefore = batchOperationRequestDto.getSearchBefore(objectMapper);
    if (searchAfter != null
        || searchBefore == null) { // this sorting is also the default one for 1st page
      sort1 =
          new FieldSortBuilder(BatchOperationTemplate.END_DATE)
              .order(SortOrder.DESC)
              .missing("_first");
      sort2 = new FieldSortBuilder(BatchOperationTemplate.START_DATE).order(SortOrder.DESC);
      querySearchAfter = searchAfter; // may be null
    } else { // searchBefore != null
      // reverse sorting
      sort1 =
          new FieldSortBuilder(BatchOperationTemplate.END_DATE)
              .order(SortOrder.ASC)
              .missing("_last");
      sort2 = new FieldSortBuilder(BatchOperationTemplate.START_DATE).order(SortOrder.ASC);
      querySearchAfter = searchBefore;
    }

    final SearchSourceBuilder sourceBuilder =
        searchSource()
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

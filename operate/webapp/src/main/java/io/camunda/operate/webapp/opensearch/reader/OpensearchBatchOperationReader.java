/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.opensearch.reader;

import static io.camunda.operate.store.opensearch.dsl.QueryDSL.constantScore;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.sortOptions;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.term;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.searchRequestBuilder;
import static io.camunda.operate.util.CollectionUtil.reversedView;
import static io.camunda.operate.util.ConversionUtils.toStringArray;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.webapp.reader.BatchOperationReader;
import io.camunda.operate.webapp.rest.dto.operation.BatchOperationRequestDto;
import io.camunda.operate.webapp.security.UserService;
import io.camunda.webapps.schema.descriptors.template.BatchOperationTemplate;
import io.camunda.webapps.schema.entities.operation.BatchOperationEntity;
import java.util.Arrays;
import java.util.List;
import org.opensearch.client.opensearch._types.SortOptions;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(OpensearchCondition.class)
@Component
public class OpensearchBatchOperationReader implements BatchOperationReader {
  @Autowired private BatchOperationTemplate batchOperationTemplate;
  @Autowired private UserService<?> userService;

  @Autowired
  @Qualifier("operateObjectMapper")
  private ObjectMapper objectMapper;

  @Autowired private RichOpenSearchClient richOpenSearchClient;

  @Override
  public List<BatchOperationEntity> getBatchOperations(
      final BatchOperationRequestDto batchOperationRequestDto) {
    final var searchRequestBuilder = createSearchRequest(batchOperationRequestDto);
    final List<BatchOperationEntity> batchOperationEntities =
        richOpenSearchClient
            .doc()
            .search(searchRequestBuilder, BatchOperationEntity.class)
            .hits()
            .hits()
            .stream()
            .map(
                hit -> {
                  final BatchOperationEntity entity = hit.source();
                  entity.setSortValues(hit.sort().toArray());
                  return entity;
                })
            .toList();

    if (batchOperationRequestDto.getSearchBefore() != null) {
      return reversedView(batchOperationEntities);
    }
    return batchOperationEntities;
  }

  private SearchRequest.Builder createSearchRequest(
      final BatchOperationRequestDto batchOperationRequestDto) {
    final SortOptions sort1, sort2;
    final Object[] querySearchAfter;

    final Object[] searchAfter = batchOperationRequestDto.getSearchAfter(objectMapper);
    final Object[] searchBefore = batchOperationRequestDto.getSearchBefore(objectMapper);
    if (searchAfter != null
        || searchBefore == null) { // this sorting is also the default one for 1st page
      sort1 = sortOptions(BatchOperationTemplate.END_DATE, SortOrder.Desc, "_first");
      sort2 = sortOptions(BatchOperationTemplate.START_DATE, SortOrder.Desc);
      querySearchAfter = searchAfter; // may be null
    } else { // searchBefore != null
      // reverse sorting
      sort1 = sortOptions(BatchOperationTemplate.END_DATE, SortOrder.Asc, "_last");
      sort2 = sortOptions(BatchOperationTemplate.START_DATE, SortOrder.Asc);
      querySearchAfter = searchBefore;
    }

    final var searchRequestBuilder =
        searchRequestBuilder(batchOperationTemplate.getAlias())
            .query(
                constantScore(
                    term(
                        BatchOperationTemplate.USERNAME,
                        userService.getCurrentUser().getUsername())))
            .sort(sort1, sort2)
            .size(batchOperationRequestDto.getPageSize());

    if (querySearchAfter != null) {
      searchRequestBuilder.searchAfter(Arrays.asList(toStringArray(querySearchAfter)));
    }

    return searchRequestBuilder;
  }
}

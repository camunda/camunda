/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.opensearch.reader;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.entities.BatchOperationEntity;
import io.camunda.operate.schema.templates.BatchOperationTemplate;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.webapp.reader.BatchOperationReader;
import io.camunda.operate.webapp.rest.dto.operation.BatchOperationRequestDto;
import io.camunda.operate.webapp.security.UserService;
import org.opensearch.client.opensearch._types.SortOptions;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

import static io.camunda.operate.store.opensearch.dsl.QueryDSL.constantScore;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.sortOptions;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.term;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.searchRequestBuilder;
import static io.camunda.operate.util.CollectionUtil.reversedView;
import static io.camunda.operate.util.ConversionUtils.toStringArray;

@Conditional(OpensearchCondition.class)
@Component
public class OpensearchBatchOperationReader implements BatchOperationReader {
  @Autowired
  private BatchOperationTemplate batchOperationTemplate;
  @Autowired
  private UserService<?> userService;
  @Autowired
  private ObjectMapper objectMapper;
  @Autowired
  private RichOpenSearchClient richOpenSearchClient;

  @Override
  public List<BatchOperationEntity> getBatchOperations(BatchOperationRequestDto batchOperationRequestDto) {
    var searchRequestBuilder = createSearchRequest(batchOperationRequestDto);
    List<BatchOperationEntity> batchOperationEntities = richOpenSearchClient.doc().search(searchRequestBuilder, BatchOperationEntity.class)
      .hits()
      .hits()
      .stream()
      .map(hit -> {
        BatchOperationEntity entity = hit.source();
        entity.setSortValues(hit.sort().toArray());
        return entity;
      })
      .toList();

      if (batchOperationRequestDto.getSearchBefore() != null) {
        return reversedView(batchOperationEntities);
      }
      return batchOperationEntities;
  }

  private SearchRequest.Builder createSearchRequest(BatchOperationRequestDto batchOperationRequestDto) {
     SortOptions sort1, sort2;
    Object[] querySearchAfter;

    Object[] searchAfter = batchOperationRequestDto.getSearchAfter(objectMapper);
    Object[] searchBefore = batchOperationRequestDto.getSearchBefore(objectMapper);
    if (searchAfter != null || searchBefore == null) { //this sorting is also the default one for 1st page
      sort1 = sortOptions(BatchOperationTemplate.END_DATE, SortOrder.Desc, "_first");
      sort2 = sortOptions(BatchOperationTemplate.START_DATE, SortOrder.Desc);
      querySearchAfter = searchAfter; //may be null
    } else { //searchBefore != null
      //reverse sorting
      sort1 = sortOptions(BatchOperationTemplate.END_DATE, SortOrder.Asc, "_last");
      sort2 = sortOptions(BatchOperationTemplate.START_DATE, SortOrder.Asc);
      querySearchAfter = searchBefore;
    }

    var searchRequestBuilder = searchRequestBuilder(batchOperationTemplate.getAlias())
      .query(constantScore(term(BatchOperationTemplate.USERNAME, userService.getCurrentUser().getUsername())))
      .sort(sort1, sort2)
      .size(batchOperationRequestDto.getPageSize());;

    if(querySearchAfter != null){
      searchRequestBuilder.searchAfter(Arrays.asList(toStringArray(querySearchAfter)));
    }

    return searchRequestBuilder;
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.opensearch.reader;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.entities.OperationEntity;
import io.camunda.operate.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.store.opensearch.dsl.RequestDSL;
import io.camunda.operate.util.CollectionUtil;
import io.camunda.operate.webapp.opensearch.OpenSearchQueryHelper;
import io.camunda.operate.webapp.reader.ListViewReader;
import io.camunda.operate.webapp.reader.OperationReader;
import io.camunda.operate.webapp.rest.dto.listview.ListViewProcessInstanceDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewRequestDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewResponseDto;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static io.camunda.operate.schema.templates.ListViewTemplate.JOIN_RELATION;
import static io.camunda.operate.schema.templates.ListViewTemplate.PROCESS_INSTANCE_JOIN_RELATION;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.*;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.QueryType.ALL;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.QueryType.ONLY_RUNTIME;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.searchRequestBuilder;

@Conditional(OpensearchCondition.class)
@Component
public class OpensearchListViewReader implements ListViewReader {
  private static final Logger logger = LoggerFactory.getLogger(OpensearchListViewReader.class);

  @Autowired
  private RichOpenSearchClient richOpenSearchClient;

  @Autowired
  private OpenSearchQueryHelper openSearchQueryHelper;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private ListViewTemplate listViewTemplate;

  @Autowired
  private OperationReader operationReader;

  /**
   * Queries process instances by different criteria (with pagination).
   * @param processInstanceRequest
   * @return
   */
  @Override
  public ListViewResponseDto queryProcessInstances(ListViewRequestDto processInstanceRequest) {
    ListViewResponseDto result = new ListViewResponseDto();

    List<ProcessInstanceForListViewEntity> processInstanceEntities = queryListView(processInstanceRequest, result);
    List<Long> processInstanceKeys = CollectionUtil
        .map(processInstanceEntities, processInstanceEntity -> Long.valueOf(processInstanceEntity.getId()));

    final Map<Long, List<OperationEntity>> operationsPerProcessInstance = operationReader.getOperationsPerProcessInstanceKey(processInstanceKeys);

    final List<ListViewProcessInstanceDto> processInstanceDtoList = ListViewProcessInstanceDto.createFrom(processInstanceEntities, operationsPerProcessInstance, objectMapper);
    result.setProcessInstances(processInstanceDtoList);
    return result;
  }

  private String getSortBy(final ListViewRequestDto request) {
    if (request.getSorting() != null) {
      String sortBy = request.getSorting().getSortBy();
      if (sortBy.equals(ListViewRequestDto.SORT_BY_PARENT_INSTANCE_ID)) {
        sortBy = ListViewTemplate.PARENT_PROCESS_INSTANCE_KEY;
      } else if (sortBy.equals(ListViewTemplate.ID)) {
        //we sort by id as numbers, not as strings
        sortBy = ListViewTemplate.KEY;
      }
      return sortBy;
    }
    return null;
  }

  private void applySorting(SearchRequest.Builder searchRequest, ListViewRequestDto request){
    final String sortBy = getSortBy(request);
    final boolean directSorting = request.getSearchAfter() != null || request.getSearchBefore() == null;
    if (request.getSorting() != null) {
      final SortOrder directOrder = "asc".equals(request.getSorting().getSortOrder()) ? SortOrder.Asc : SortOrder.Desc;
      if (directSorting) {
        searchRequest.sort(sortOptions(sortBy, directOrder, "_last"));
      } else {
        searchRequest.sort(sortOptions(sortBy, reverseOrder(directOrder), "_first"));
      }
    }

    Object[] querySearchAfter;
    if (directSorting) {
      searchRequest.sort(sortOptions(ListViewTemplate.KEY, SortOrder.Asc));
      querySearchAfter = request.getSearchAfter(objectMapper);
    } else {
      searchRequest.sort(sortOptions(ListViewTemplate.KEY, SortOrder.Desc));
      querySearchAfter = request.getSearchBefore(objectMapper);
    }
    searchRequest.size(request.getPageSize());
    if(querySearchAfter != null) {
      searchRequest.searchAfter(CollectionUtil.toSafeListOfStrings(querySearchAfter));
    }
  }

  @Override
  public List<ProcessInstanceForListViewEntity> queryListView(ListViewRequestDto processInstanceRequest, ListViewResponseDto result) {
    final RequestDSL.QueryType queryType = processInstanceRequest.getQuery().isFinished() ? ALL : ONLY_RUNTIME;
    final Query query = constantScore(withTenantCheck(
      and(
        term(JOIN_RELATION, PROCESS_INSTANCE_JOIN_RELATION),
        openSearchQueryHelper.createQueryFragment(processInstanceRequest.getQuery())
      )
    ));

    logger.debug("Process instance search request: \n{}", query.toString());

    var searchRequestBuilder = searchRequestBuilder(listViewTemplate, queryType).query(query);

    applySorting(searchRequestBuilder, processInstanceRequest);

    searchRequestBuilder.size(processInstanceRequest.getPageSize());

    final SearchResponse<ProcessInstanceForListViewEntity> response = richOpenSearchClient.doc().search(searchRequestBuilder, ProcessInstanceForListViewEntity.class);

    result.setTotalCount(response.hits().total().value());

    List<ProcessInstanceForListViewEntity> processInstanceEntities = response
      .hits()
      .hits()
      .stream()
      .map(hit -> {
        ProcessInstanceForListViewEntity entity = hit.source();;
        entity.setSortValues(hit.sort().toArray());
        return entity;
      })
      .toList();

    if (processInstanceRequest.getSearchBefore() != null) {
      return CollectionUtil.reversedView(processInstanceEntities);
    }

    return processInstanceEntities;
  }
}

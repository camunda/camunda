/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.elasticsearch.reader;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.conditions.ElasticsearchCondition;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import io.camunda.operate.util.CollectionUtil;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.entities.OperationEntity;
import io.camunda.operate.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.tenant.TenantAwareElasticsearchClient;
import io.camunda.operate.webapp.elasticsearch.QueryHelper;
import io.camunda.operate.webapp.reader.OperationReader;
import io.camunda.operate.webapp.rest.dto.listview.ListViewProcessInstanceDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewRequestDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewResponseDto;
import io.camunda.operate.webapp.security.identity.PermissionsService;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(ElasticsearchCondition.class)
@Component
public class ListViewReader implements io.camunda.operate.webapp.reader.ListViewReader {

  private static final String WILD_CARD = "*";

  private static final Logger logger = LoggerFactory.getLogger(ListViewReader.class);

  @Autowired
  private TenantAwareElasticsearchClient tenantAwareClient;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private OperationReader operationReader;

  @Autowired
  private QueryHelper queryHelper;

  @Autowired
  private OperateProperties operateProperties;

  @Autowired
  private DateTimeFormatter dateTimeFormatter;


  @Autowired(required = false)
  private PermissionsService permissionsService;

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

  @Override
  public List<ProcessInstanceForListViewEntity> queryListView(
          ListViewRequestDto processInstanceRequest, ListViewResponseDto result) {

    final QueryBuilder query = queryHelper.createRequestQuery(processInstanceRequest.getQuery());

    logger.debug("Process instance search request: \n{}", query.toString());

    final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
        .query(query);

    applySorting(searchSourceBuilder, processInstanceRequest);

    SearchRequest searchRequest = queryHelper.createSearchRequest(processInstanceRequest.getQuery())
        .source(searchSourceBuilder);

    logger.debug("Search request will search in: \n{}", searchRequest.indices());

    try {
      SearchResponse response = tenantAwareClient.search(searchRequest);
      result.setTotalCount(response.getHits().getTotalHits().value);

      List<ProcessInstanceForListViewEntity> processInstanceEntities = ElasticsearchUtil.mapSearchHits(response.getHits().getHits(),
          (sh) -> {
            ProcessInstanceForListViewEntity entity = ElasticsearchUtil.fromSearchHit(sh.getSourceAsString(), objectMapper, ProcessInstanceForListViewEntity.class);
            entity.setSortValues(sh.getSortValues());
            return entity;
          });
      if (processInstanceRequest.getSearchBefore() != null) {
        Collections.reverse(processInstanceEntities);
      }
      return processInstanceEntities;
    } catch (IOException e) {
      final String message = String
          .format("Exception occurred, while obtaining instances list: %s", e.getMessage());
      logger.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  private void applySorting(SearchSourceBuilder searchSourceBuilder, ListViewRequestDto request) {

    String sortBy = getSortBy(request);

    final boolean directSorting = request.getSearchAfter() != null || request.getSearchBefore() == null;
    if (request.getSorting() != null) {
      SortBuilder sort1;
      SortOrder sort1DirectOrder = SortOrder.fromString(request.getSorting().getSortOrder());
      if (directSorting) {
        sort1 = SortBuilders.fieldSort(sortBy).order(sort1DirectOrder)
            .missing("_last");
      } else {
        sort1 = SortBuilders.fieldSort(sortBy)
            .order(reverseOrder(sort1DirectOrder)).missing("_first");
      }
      searchSourceBuilder.sort(sort1);
    }

    SortBuilder sort2;
    Object[] querySearchAfter;
    if (directSorting) { //this sorting is also the default one for 1st page
      sort2 = SortBuilders.fieldSort(ListViewTemplate.KEY).order(SortOrder.ASC);
      querySearchAfter = request.getSearchAfter(objectMapper); //may be null
    } else { //searchBefore != null
      //reverse sorting
      sort2 = SortBuilders.fieldSort(ListViewTemplate.KEY).order(SortOrder.DESC);
      querySearchAfter = request.getSearchBefore(objectMapper);
    }

    searchSourceBuilder
        .sort(sort2)
        .size(request.getPageSize());
    if (querySearchAfter != null) {
      searchSourceBuilder.searchAfter(querySearchAfter);
    }
  }

  private String getSortBy(final ListViewRequestDto request) {
    if (request.getSorting() != null) {
      String sortBy = request.getSorting().getSortBy();
      if (sortBy.equals(ListViewRequestDto.SORT_BY_PARENT_INSTANCE_ID)) {
        sortBy = ListViewTemplate.PARENT_PROCESS_INSTANCE_KEY;
      } else if (sortBy.equals(ListViewRequestDto.SORT_BY_TENANT_ID)) {
        sortBy = ListViewTemplate.TENANT_ID;
      } if (sortBy.equals(ListViewTemplate.ID)) {
        //we sort by id as numbers, not as strings
        sortBy = ListViewTemplate.KEY;
      }
      return sortBy;
    }
    return null;
  }

  private SortOrder reverseOrder(final SortOrder sortOrder) {
    if (sortOrder.equals(SortOrder.ASC)) {
      return SortOrder.DESC;
    } else {
      return SortOrder.ASC;
    }
  }

}

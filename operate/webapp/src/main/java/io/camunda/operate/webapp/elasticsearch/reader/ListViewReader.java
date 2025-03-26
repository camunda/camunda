/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.elasticsearch.reader;

import static io.camunda.operate.util.ElasticsearchUtil.reverseOrder;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.PARENT_FLOW_NODE_INSTANCE_KEY;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.tenant.TenantAwareElasticsearchClient;
import io.camunda.operate.util.CollectionUtil;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.util.Tuple;
import io.camunda.operate.webapp.elasticsearch.QueryHelper;
import io.camunda.operate.webapp.reader.OperationReader;
import io.camunda.operate.webapp.rest.dto.listview.ListViewProcessInstanceDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewRequestDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewResponseDto;
import io.camunda.operate.webapp.security.permission.PermissionsService;
import io.camunda.webapps.schema.descriptors.template.ListViewTemplate;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.webapps.schema.entities.operation.OperationEntity;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(ElasticsearchCondition.class)
@Component
public class ListViewReader implements io.camunda.operate.webapp.reader.ListViewReader {

  private static final Logger LOGGER = LoggerFactory.getLogger(ListViewReader.class);

  @Autowired private TenantAwareElasticsearchClient tenantAwareClient;

  @Autowired
  @Qualifier("operateObjectMapper")
  private ObjectMapper objectMapper;

  @Autowired private OperationReader operationReader;

  @Autowired private QueryHelper queryHelper;

  @Autowired private OperateProperties operateProperties;

  @Autowired private DateTimeFormatter dateTimeFormatter;

  @Autowired private ListViewTemplate listViewTemplate;

  @Autowired private PermissionsService permissionsService;

  /**
   * Queries process instances by different criteria (with pagination).
   *
   * @param processInstanceRequest
   * @return
   */
  @Override
  public ListViewResponseDto queryProcessInstances(
      final ListViewRequestDto processInstanceRequest) {
    final ListViewResponseDto result = new ListViewResponseDto();

    final List<ProcessInstanceForListViewEntity> processInstanceEntities =
        queryListView(processInstanceRequest, result);
    final List<Long> processInstanceKeys =
        CollectionUtil.map(
            processInstanceEntities,
            processInstanceEntity -> Long.valueOf(processInstanceEntity.getId()));

    final Map<Long, List<OperationEntity>> operationsPerProcessInstance =
        operationReader.getOperationsPerProcessInstanceKey(processInstanceKeys);

    final List<ListViewProcessInstanceDto> processInstanceDtoList =
        ListViewProcessInstanceDto.createFrom(
            processInstanceEntities,
            operationsPerProcessInstance,
            permissionsService,
            objectMapper);
    result.setProcessInstances(processInstanceDtoList);
    return result;
  }

  @Override
  public List<ProcessInstanceForListViewEntity> queryListView(
      final ListViewRequestDto processInstanceRequest, final ListViewResponseDto result) {

    final QueryBuilder query = queryHelper.createRequestQuery(processInstanceRequest.getQuery());

    LOGGER.debug("Process instance search request: \n{}", query.toString());

    final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder().query(query);

    applySorting(searchSourceBuilder, processInstanceRequest);

    final SearchRequest searchRequest =
        queryHelper
            .createSearchRequest(processInstanceRequest.getQuery())
            .source(searchSourceBuilder);

    LOGGER.debug("Search request will search in: \n{}", searchRequest.indices());

    try {
      final SearchResponse response = tenantAwareClient.search(searchRequest);
      result.setTotalCount(response.getHits().getTotalHits().value);

      final List<ProcessInstanceForListViewEntity> processInstanceEntities =
          ElasticsearchUtil.mapSearchHits(
              response.getHits().getHits(),
              (sh) -> {
                final ProcessInstanceForListViewEntity entity =
                    ElasticsearchUtil.fromSearchHit(
                        sh.getSourceAsString(),
                        objectMapper,
                        ProcessInstanceForListViewEntity.class);
                entity.setSortValues(sh.getSortValues());
                return entity;
              });
      if (processInstanceRequest.getSearchBefore() != null) {
        Collections.reverse(processInstanceEntities);
      }
      return processInstanceEntities;
    } catch (final IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining instances list: %s", e.getMessage());
      LOGGER.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  @Override
  public Tuple<String, String> getCalledProcessInstanceIdAndNameByFlowNodeInstanceId(
      final String flowNodeInstanceId) {
    final String[] calledProcessInstanceId = {null};
    final String[] calledProcessDefinitionName = {null};
    findCalledProcessInstance(
        flowNodeInstanceId,
        sh -> {
          calledProcessInstanceId[0] = sh.getId();
          final Map<String, Object> source = sh.getSourceAsMap();
          String processName = (String) source.get(ListViewTemplate.PROCESS_NAME);
          if (processName == null) {
            processName = (String) source.get(ListViewTemplate.BPMN_PROCESS_ID);
          }
          calledProcessDefinitionName[0] = processName;
        });
    return Tuple.of(calledProcessInstanceId[0], calledProcessDefinitionName[0]);
  }

  private void applySorting(
      final SearchSourceBuilder searchSourceBuilder, final ListViewRequestDto request) {

    final String sortBy = getSortBy(request);

    final boolean directSorting =
        request.getSearchAfter() != null || request.getSearchBefore() == null;
    if (request.getSorting() != null) {
      final SortBuilder sort1;
      final SortOrder sort1DirectOrder = SortOrder.fromString(request.getSorting().getSortOrder());
      if (directSorting) {
        sort1 = SortBuilders.fieldSort(sortBy).order(sort1DirectOrder).missing("_last");
      } else {
        sort1 =
            SortBuilders.fieldSort(sortBy).order(reverseOrder(sort1DirectOrder)).missing("_first");
      }
      searchSourceBuilder.sort(sort1);
    }

    final SortBuilder sort2;
    final Object[] querySearchAfter;
    if (directSorting) { // this sorting is also the default one for 1st page
      sort2 = SortBuilders.fieldSort(ListViewTemplate.KEY).order(SortOrder.ASC);
      querySearchAfter = request.getSearchAfter(objectMapper); // may be null
    } else { // searchBefore != null
      // reverse sorting
      sort2 = SortBuilders.fieldSort(ListViewTemplate.KEY).order(SortOrder.DESC);
      querySearchAfter = request.getSearchBefore(objectMapper);
    }

    searchSourceBuilder.sort(sort2).size(request.getPageSize());
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
      }
      if (sortBy.equals(ListViewTemplate.ID)) {
        // we sort by id as numbers, not as strings
        sortBy = ListViewTemplate.KEY;
      }
      return sortBy;
    }
    return null;
  }

  private void findCalledProcessInstance(
      final String flowNodeInstanceId, final Consumer<SearchHit> processInstanceConsumer) {
    final TermQueryBuilder parentFlowNodeInstanceQ =
        termQuery(PARENT_FLOW_NODE_INSTANCE_KEY, flowNodeInstanceId);
    final SearchRequest request =
        ElasticsearchUtil.createSearchRequest(listViewTemplate)
            .source(
                new SearchSourceBuilder()
                    .query(parentFlowNodeInstanceQ)
                    .fetchSource(
                        new String[] {
                          ListViewTemplate.PROCESS_NAME, ListViewTemplate.BPMN_PROCESS_ID
                        },
                        null));
    try {
      final SearchResponse response = tenantAwareClient.search(request);
      if (response.getHits().getTotalHits().value >= 1) {
        processInstanceConsumer.accept(response.getHits().getAt(0));
      }
    } catch (final IOException e) {
      final String message =
          String.format(
              "Exception occurred, while obtaining parent process instance id for flow node instance: %s",
              e.getMessage());
      throw new OperateRuntimeException(message, e);
    }
  }
}

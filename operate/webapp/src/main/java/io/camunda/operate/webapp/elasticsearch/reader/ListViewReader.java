/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.elasticsearch.reader;

import static io.camunda.operate.util.ElasticsearchUtil.MAP_CLASS;
import static io.camunda.operate.util.ElasticsearchUtil.reverseOrder;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.PARENT_FLOW_NODE_INSTANCE_KEY;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.property.OperateProperties;
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
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(ElasticsearchCondition.class)
@Component
public class ListViewReader extends AbstractReader
    implements io.camunda.operate.webapp.reader.ListViewReader {

  private static final Logger LOGGER = LoggerFactory.getLogger(ListViewReader.class);

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

    final Query query = queryHelper.createRequestQuery(processInstanceRequest.getQuery());
    final Query tenantAwareQuery = tenantHelper.makeQueryTenantAware(query);

    LOGGER.debug("Process instance search request: \n{}", tenantAwareQuery.toString());

    final SearchRequest.Builder searchRequestBuilder =
        queryHelper.createSearchRequest(processInstanceRequest.getQuery()).query(tenantAwareQuery);

    applySorting(searchRequestBuilder, processInstanceRequest);

    final SearchRequest searchRequest = searchRequestBuilder.build();

    LOGGER.debug("Search request will search in: \n{}", searchRequest.index());

    try {
      final var response = es8client.search(searchRequest, ProcessInstanceForListViewEntity.class);
      result.setTotalCount(response.hits().total().value());

      final List<ProcessInstanceForListViewEntity> processInstanceEntities =
          response.hits().hits().stream()
              .map(
                  hit -> {
                    final ProcessInstanceForListViewEntity entity = hit.source();
                    entity.setSortValues(hit.sort().stream().map(FieldValue::_get).toArray());
                    return entity;
                  })
              .toList();
      if (processInstanceRequest.getSearchBefore() != null) {
        return ImmutableList.copyOf(processInstanceEntities).reverse();
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
    final Query parentFlowNodeInstanceQ =
        ElasticsearchUtil.termsQuery(PARENT_FLOW_NODE_INSTANCE_KEY, flowNodeInstanceId);
    final Query tenantAwareQuery = tenantHelper.makeQueryTenantAware(parentFlowNodeInstanceQ);

    final SearchRequest request =
        SearchRequest.of(
            s ->
                s.index(listViewTemplate.getAlias())
                    .query(tenantAwareQuery)
                    .source(
                        src ->
                            src.filter(
                                f ->
                                    f.includes(
                                        ListViewTemplate.PROCESS_NAME,
                                        ListViewTemplate.BPMN_PROCESS_ID))));
    try {
      final var response = es8client.search(request, MAP_CLASS);

      if (response.hits().total().value() < 1) {
        return Tuple.of(null, null);
      }

      final var hit = response.hits().hits().get(0);
      final String processInstanceId = hit.id();
      final Map<String, Object> source = hit.source();
      String processName = (String) source.get(ListViewTemplate.PROCESS_NAME);
      if (processName == null) {
        processName = (String) source.get(ListViewTemplate.BPMN_PROCESS_ID);
      }
      return Tuple.of(processInstanceId, processName);
    } catch (final IOException e) {
      final String message =
          String.format(
              "Exception occurred, while obtaining parent process instance id for flow node instance: %s",
              e.getMessage());
      throw new OperateRuntimeException(message, e);
    }
  }

  private void applySorting(
      final SearchRequest.Builder searchRequestBuilder, final ListViewRequestDto request) {

    final String sortBy = getSortBy(request);

    final boolean directSorting =
        request.getSearchAfter() != null || request.getSearchBefore() == null;
    if (request.getSorting() != null) {
      final SortOrder sort1DirectOrder =
          ElasticsearchUtil.toSortOrder(request.getSorting().getSortOrder());
      if (directSorting) {
        searchRequestBuilder.sort(ElasticsearchUtil.sortOrder(sortBy, sort1DirectOrder, "_last"));
      } else {
        searchRequestBuilder.sort(
            ElasticsearchUtil.sortOrder(sortBy, reverseOrder(sort1DirectOrder), "_first"));
      }
    }

    final Object[] querySearchAfter;
    if (directSorting) { // this sorting is also the default one for 1st page
      searchRequestBuilder.sort(ElasticsearchUtil.sortOrder(ListViewTemplate.KEY, SortOrder.Asc));
      querySearchAfter = request.getSearchAfter(objectMapper); // may be null
    } else { // searchBefore != null
      // reverse sorting
      searchRequestBuilder.sort(ElasticsearchUtil.sortOrder(ListViewTemplate.KEY, SortOrder.Desc));
      querySearchAfter = request.getSearchBefore(objectMapper);
    }

    searchRequestBuilder.size(request.getPageSize());
    if (querySearchAfter != null) {
      searchRequestBuilder.searchAfter(
          ElasticsearchUtil.searchAfterToFieldValues(querySearchAfter));
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
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.store.elasticsearch;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.data.conditionals.ElasticSearchCondition;
import io.camunda.tasklist.entities.ProcessEntity;
import io.camunda.tasklist.exceptions.NotFoundException;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.property.IdentityProperties;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.schema.indices.ProcessIndex;
import io.camunda.tasklist.store.FormStore;
import io.camunda.tasklist.store.ProcessStore;
import io.camunda.tasklist.util.ElasticsearchUtil;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.collapse.CollapseBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class ProcessStoreElasticSearch implements ProcessStore {

  private static final Logger LOGGER = LoggerFactory.getLogger(ProcessStoreElasticSearch.class);

  private static final Boolean CASE_INSENSITIVE = true;

  @Autowired private ProcessIndex processIndex;

  @Autowired private RestHighLevelClient esClient;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private TasklistProperties tasklistProperties;

  @Autowired private FormStore formStore;

  public ProcessEntity getProcessByProcessDefinitionKey(String processDefinitionKey) {
    final QueryBuilder qb = QueryBuilders.termQuery(ProcessIndex.KEY, processDefinitionKey);

    final SearchRequest searchRequest =
        new SearchRequest(processIndex.getAlias())
            .source(
                new SearchSourceBuilder()
                    .query(qb)
                    .collapse(new CollapseBuilder(ProcessIndex.KEY))
                    .sort(SortBuilders.fieldSort(ProcessIndex.VERSION).order(SortOrder.DESC))
                    .size(1));
    try {
      final SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);
      if (response.getHits().getTotalHits().value > 0) {
        final ProcessEntity processEntity =
            fromSearchHit(response.getHits().getHits()[0].getSourceAsString());
        return processEntity;
      } else {
        throw new NotFoundException(
            String.format("Process with key %s not found", processDefinitionKey));
      }
    } catch (IOException e) {
      throw new TasklistRuntimeException(e);
    }
  }

  /** Uses form reader to retrieve the start event form id when exists. */
  public String getStartEventFormIdByBpmnProcess(ProcessEntity process) {
    if (process.isStartedByForm()) {
      final String formId = StringUtils.substringAfterLast(process.getFormKey(), ":");
      final var form = formStore.getForm(formId, process.getId());
      return form.getId();
    }
    return null;
  }

  /** Gets the process by id. */
  public ProcessEntity getProcessByBpmnProcessId(String bpmnProcessId) {
    final QueryBuilder qb =
        QueryBuilders.termQuery(ProcessIndex.PROCESS_DEFINITION_ID, bpmnProcessId);

    final SearchRequest searchRequest =
        new SearchRequest(processIndex.getAlias())
            .source(
                new SearchSourceBuilder()
                    .query(qb)
                    .collapse(new CollapseBuilder(ProcessIndex.PROCESS_DEFINITION_ID))
                    .sort(SortBuilders.fieldSort(ProcessIndex.VERSION).order(SortOrder.DESC))
                    .size(1));
    try {
      final SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);
      if (response.getHits().getTotalHits().value > 0) {
        final ProcessEntity processEntity =
            fromSearchHit(response.getHits().getHits()[0].getSourceAsString());
        return processEntity;
      } else {
        throw new NotFoundException(
            String.format("Could not find process with id '%s'.", bpmnProcessId));
      }
    } catch (IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining the process: %s", e.getMessage());
      throw new TasklistRuntimeException(message, e);
    }
  }

  public ProcessEntity getProcess(String processId) {
    final SearchRequest searchRequest =
        new SearchRequest(processIndex.getAlias())
            .source(
                new SearchSourceBuilder()
                    .query(QueryBuilders.termQuery(ProcessIndex.KEY, processId)));

    try {
      final SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);
      if (response.getHits().getTotalHits().value == 1) {
        return fromSearchHit(response.getHits().getHits()[0].getSourceAsString());
      } else if (response.getHits().getTotalHits().value > 1) {
        throw new TasklistRuntimeException(
            String.format("Could not find unique process with id '%s'.", processId));
      } else {
        throw new NotFoundException(
            String.format("Could not find process with id '%s'.", processId));
      }
    } catch (IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining the process: %s", e.getMessage());
      throw new TasklistRuntimeException(message, e);
    }
  }

  private ProcessEntity fromSearchHit(String processString) {
    return ElasticsearchUtil.fromSearchHit(processString, objectMapper, ProcessEntity.class);
  }

  public List<ProcessEntity> getProcesses(final List<String> processDefinitions) {
    final QueryBuilder qb;

    if (tasklistProperties.getIdentity().isResourcePermissionsEnabled()) {
      if (processDefinitions.size() == 0) {
        return new ArrayList<>();
      }

      if (processDefinitions.contains(IdentityProperties.ALL_RESOURCES)) {
        qb =
            QueryBuilders.boolQuery()
                .must(QueryBuilders.existsQuery(ProcessIndex.PROCESS_DEFINITION_ID))
                .mustNot(QueryBuilders.termQuery(ProcessIndex.PROCESS_DEFINITION_ID, ""));
      } else {

        qb =
            QueryBuilders.boolQuery()
                .must(
                    QueryBuilders.termsQuery(
                        ProcessIndex.PROCESS_DEFINITION_ID, processDefinitions))
                .must(QueryBuilders.existsQuery(ProcessIndex.PROCESS_DEFINITION_ID))
                .mustNot(QueryBuilders.termQuery(ProcessIndex.PROCESS_DEFINITION_ID, ""));
      }

    } else {
      qb =
          QueryBuilders.boolQuery()
              .must(QueryBuilders.existsQuery(ProcessIndex.PROCESS_DEFINITION_ID))
              .mustNot(QueryBuilders.termQuery(ProcessIndex.PROCESS_DEFINITION_ID, ""));
    }

    final SearchRequest searchRequest = getSearchRequestUniqueByProcessDefinitionId(qb);
    final SearchResponse response;
    try {
      response = esClient.search(searchRequest, RequestOptions.DEFAULT);
      return mapResponse(response);
    } catch (IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining the process: %s", e.getMessage());
      throw new TasklistRuntimeException(message, e);
    }
  }

  public List<ProcessEntity> getProcesses(String search, final List<String> processDefinitions) {

    if (search == null || search.isBlank()) {
      return getProcesses(processDefinitions);
    }

    final QueryBuilder qb;
    final String regexSearch = String.format(".*%s.*", search);

    if (tasklistProperties.getIdentity().isResourcePermissionsEnabled()) {

      if (processDefinitions.size() == 0) {
        return new ArrayList<>();
      }

      if (processDefinitions.contains(IdentityProperties.ALL_RESOURCES)) {
        qb =
            QueryBuilders.boolQuery()
                .should(QueryBuilders.termQuery(ProcessIndex.ID, search))
                .should(
                    QueryBuilders.regexpQuery(ProcessIndex.NAME, regexSearch)
                        .caseInsensitive(CASE_INSENSITIVE))
                .should(
                    QueryBuilders.regexpQuery(ProcessIndex.PROCESS_DEFINITION_ID, regexSearch)
                        .caseInsensitive(CASE_INSENSITIVE))
                .must(QueryBuilders.existsQuery(ProcessIndex.PROCESS_DEFINITION_ID))
                .mustNot(QueryBuilders.termQuery(ProcessIndex.PROCESS_DEFINITION_ID, ""))
                .minimumShouldMatch(1);
      } else {
        qb =
            QueryBuilders.boolQuery()
                .should(QueryBuilders.termQuery(ProcessIndex.ID, search))
                .should(
                    QueryBuilders.regexpQuery(ProcessIndex.NAME, regexSearch)
                        .caseInsensitive(CASE_INSENSITIVE))
                .should(
                    QueryBuilders.regexpQuery(ProcessIndex.PROCESS_DEFINITION_ID, regexSearch)
                        .caseInsensitive(CASE_INSENSITIVE))
                .must(
                    QueryBuilders.termsQuery(
                        ProcessIndex.PROCESS_DEFINITION_ID, processDefinitions))
                .must(QueryBuilders.existsQuery(ProcessIndex.PROCESS_DEFINITION_ID))
                .mustNot(QueryBuilders.termQuery(ProcessIndex.PROCESS_DEFINITION_ID, ""))
                .minimumShouldMatch(1);
      }

    } else {

      qb =
          QueryBuilders.boolQuery()
              .should(QueryBuilders.termQuery(ProcessIndex.ID, search))
              .should(
                  QueryBuilders.regexpQuery(ProcessIndex.NAME, regexSearch)
                      .caseInsensitive(CASE_INSENSITIVE))
              .should(
                  QueryBuilders.regexpQuery(ProcessIndex.PROCESS_DEFINITION_ID, regexSearch)
                      .caseInsensitive(CASE_INSENSITIVE))
              .must(QueryBuilders.existsQuery(ProcessIndex.PROCESS_DEFINITION_ID))
              .mustNot(QueryBuilders.termQuery(ProcessIndex.PROCESS_DEFINITION_ID, ""))
              .minimumShouldMatch(1);
    }

    final SearchRequest searchRequest = getSearchRequestUniqueByProcessDefinitionId(qb);

    try {
      final SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);

      return mapResponse(response);

    } catch (IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining the process: %s", e.getMessage());
      throw new TasklistRuntimeException(message, e);
    }
  }

  private SearchRequest getSearchRequestUniqueByProcessDefinitionId(QueryBuilder qb) {
    return new SearchRequest(processIndex.getAlias())
        .source(
            new SearchSourceBuilder()
                .query(qb)
                .collapse(new CollapseBuilder(ProcessIndex.PROCESS_DEFINITION_ID))
                .sort(SortBuilders.fieldSort(ProcessIndex.VERSION).order(SortOrder.DESC))
                .size(ElasticsearchUtil.QUERY_MAX_SIZE));
  }

  private List<ProcessEntity> mapResponse(SearchResponse response) {
    return ElasticsearchUtil.mapSearchHits(
        response.getHits().getHits(),
        (sh) ->
            ElasticsearchUtil.fromSearchHit(
                sh.getSourceAsString(), objectMapper, ProcessEntity.class));
  }

  public List<ProcessEntity> getProcessesStartedByForm() {
    final QueryBuilder qb;

    qb =
        QueryBuilders.boolQuery()
            .must(QueryBuilders.existsQuery(ProcessIndex.PROCESS_DEFINITION_ID))
            .mustNot(QueryBuilders.termQuery(ProcessIndex.PROCESS_DEFINITION_ID, ""));

    final SearchRequest searchRequest =
        new SearchRequest(processIndex.getAlias())
            .source(
                new SearchSourceBuilder()
                    .query(qb)
                    .collapse(new CollapseBuilder(ProcessIndex.PROCESS_DEFINITION_ID))
                    .sort(SortBuilders.fieldSort(ProcessIndex.VERSION).order(SortOrder.DESC)));

    final SearchResponse response;
    try {
      response = esClient.search(searchRequest, RequestOptions.DEFAULT);
      return mapResponse(response).stream()
          .filter(p -> p.isStartedByForm())
          .collect(Collectors.toList());
    } catch (IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining the process: %s", e.getMessage());
      throw new TasklistRuntimeException(message, e);
    }
  }
}

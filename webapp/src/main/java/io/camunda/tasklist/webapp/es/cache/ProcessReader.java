/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.es.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.identity.sdk.Identity;
import io.camunda.tasklist.entities.ProcessEntity;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.schema.indices.ProcessIndex;
import io.camunda.tasklist.util.ElasticsearchUtil;
import io.camunda.tasklist.util.SpringContextHolder;
import io.camunda.tasklist.webapp.graphql.entity.ProcessDTO;
import io.camunda.tasklist.webapp.security.identity.IdentityAuthentication;
import io.camunda.tasklist.webapp.security.identity.IdentityAuthorization;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class ProcessReader {

  private static final Logger LOGGER = LoggerFactory.getLogger(ProcessReader.class);

  private static final Boolean CASE_INSENSITIVE = true;

  @Autowired private ProcessIndex processIndex;

  @Autowired private RestHighLevelClient esClient;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private TasklistProperties tasklistProperties;

  /** Gets the process by id. */
  public ProcessDTO getProcessByBpmnProcessId(String bpmnProcessId) {
    final SearchRequest searchRequest =
        new SearchRequest(processIndex.getAlias())
            .source(
                new SearchSourceBuilder()
                    .query(
                        QueryBuilders.termQuery(ProcessIndex.PROCESS_DEFINITION_ID, bpmnProcessId))
                    .collapse(new CollapseBuilder(ProcessIndex.PROCESS_DEFINITION_ID))
                    .sort(SortBuilders.fieldSort(ProcessIndex.VERSION).order(SortOrder.DESC)));
    try {
      final SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);
      if (response.getHits().getTotalHits().value == 1) {
        final ProcessEntity processEntity =
            fromSearchHit(response.getHits().getHits()[0].getSourceAsString());
        return ProcessDTO.createFrom(processEntity, objectMapper);
      } else if (response.getHits().getTotalHits().value > 1) {
        throw new TasklistRuntimeException(
            String.format("Could not find unique process with id '%s'.", bpmnProcessId));
      } else {
        throw new TasklistRuntimeException(
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
        throw new TasklistRuntimeException(
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

  public List<ProcessDTO> getProcesses() {
    final QueryBuilder qb;
    final List<String> processDefinitions;

    processDefinitions = getProcessDefinitionsFromAuthorization();

    if (tasklistProperties.isSelfManaged()) {

      if (processDefinitions.size() == 0) {
        return new ArrayList<ProcessDTO>();
      }

      if (processDefinitions.contains(IdentityAuthorization.ALL_RESOURCES)) {
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

  private List<String> getProcessDefinitionsFromAuthorization() {
    if (tasklistProperties.getIdentity().isResourcePermissionsEnabled()
        && tasklistProperties.getIdentity().getBaseUrl() != null) {
      final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
      if (authentication instanceof IdentityAuthentication) {
        final IdentityAuthentication identityAuthentication =
            (IdentityAuthentication) authentication;
        return identityAuthentication.getAuthorizations().getProcessesAllowedToStart();
      } else if (authentication instanceof JwtAuthenticationToken) {
        final JwtAuthenticationToken jwtAuthenticationToken =
            (JwtAuthenticationToken) authentication;
        final Identity identity = SpringContextHolder.getBean(Identity.class);
        return new IdentityAuthorization(
                identity
                    .authorizations()
                    .forToken(jwtAuthenticationToken.getToken().getTokenValue()))
            .getProcessesAllowedToStart();
      }
    }
    return new ArrayList<String>();
  }

  public List<ProcessDTO> getProcesses(String search) {

    if (search == null || search.isBlank()) {
      return getProcesses();
    }

    final QueryBuilder qb;
    final List<String> processDefinitions = getProcessDefinitionsFromAuthorization();
    final String regexSearch = String.format(".*%s.*", search);

    if (tasklistProperties.isSelfManaged()) {

      if (processDefinitions.size() == 0) {
        return new ArrayList<ProcessDTO>();
      }

      if (processDefinitions.contains(IdentityAuthorization.ALL_RESOURCES)) {
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
                .sort(SortBuilders.fieldSort(ProcessIndex.VERSION).order(SortOrder.DESC)));
  }

  private List<ProcessDTO> mapResponse(SearchResponse response) {
    final List<ProcessDTO> processes =
        ElasticsearchUtil.mapSearchHits(
            response.getHits().getHits(),
            (sh) -> {
              final ProcessDTO entity =
                  ProcessDTO.createFrom(
                      ElasticsearchUtil.fromSearchHit(
                          sh.getSourceAsString(), objectMapper, ProcessEntity.class),
                      sh.getSortValues(),
                      objectMapper);
              return entity;
            });
    return processes;
  }

  public List<ProcessDTO> getProcessesStartedByForm() {
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

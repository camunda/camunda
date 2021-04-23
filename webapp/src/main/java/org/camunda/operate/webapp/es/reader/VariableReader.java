/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.webapp.es.reader;

import static org.camunda.operate.schema.templates.VariableTemplate.NAME;
import static org.camunda.operate.util.ElasticsearchUtil.QueryType.ALL;
import static org.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static org.elasticsearch.index.query.QueryBuilders.constantScoreQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.camunda.operate.entities.OperationEntity;
import org.camunda.operate.entities.VariableEntity;
import org.camunda.operate.exceptions.OperateRuntimeException;
import org.camunda.operate.schema.templates.VariableTemplate;
import org.camunda.operate.util.ElasticsearchUtil;
import org.camunda.operate.webapp.rest.dto.VariableDto;
import org.camunda.operate.webapp.rest.dto.VariableRequestDto;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.index.query.ConstantScoreQueryBuilder;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class VariableReader extends AbstractReader {

  private static final Logger logger = LoggerFactory.getLogger(VariableReader.class);

  @Autowired
  private VariableTemplate variableTemplate;

  @Autowired
  private OperationReader operationReader;

  public List<VariableDto> getVariables(final String processInstanceId,
      VariableRequestDto request) {
    List<VariableDto> response = queryVariables(processInstanceId, request);

    //query one additional instance
    if (request.getSearchAfterOrEqual() != null || request.getSearchBeforeOrEqual() != null)  {
      adjustResponse(response, processInstanceId, request);
    }

    if (response.size() > 0
        && (request.getSearchAfter() != null || request.getSearchAfterOrEqual() != null)) {
      final VariableDto firstVar = response.get(0);
      firstVar.setIsFirst(checkVarIsFirst(processInstanceId, request, firstVar.getId()));
    }

    return response;
  }

  private boolean checkVarIsFirst(
      final String processInstanceId, final VariableRequestDto query, final String id) {
    final VariableRequestDto newQuery =
        query
            .createCopy()
            .setSearchAfter(null)
            .setSearchAfterOrEqual(null)
            .setSearchBefore(null)
            .setSearchBeforeOrEqual(null)
            .setPageSize(1);
    final List<VariableDto> vars = queryVariables(processInstanceId, newQuery);
    if (vars.size() > 0) {
      return vars.get(0).getId().equals(id);
    } else {
      return false;
    }
  }

  /**
   * In case of searchAfterOrEqual and searchBeforeOrEqual add additional entity either at the
   * beginning of the list, or at the end, to conform with "orEqual" part.
   *
   * @param response
   * @param request
   */
  private void adjustResponse(
      final List<VariableDto> response,
      final String processInstanceId,
      final VariableRequestDto request) {
    String variableName = null;
    if (request.getSearchAfterOrEqual() != null) {
      variableName = (String) request.getSearchAfterOrEqual()[0];
    } else if (request.getSearchBeforeOrEqual() != null) {
      variableName = (String) request.getSearchBeforeOrEqual()[0];
    }

    VariableRequestDto newRequest = request.createCopy()
        .setSearchAfter(null)
        .setSearchAfterOrEqual(null)
        .setSearchBefore(null)
        .setSearchBeforeOrEqual(null);

    final List<VariableDto> entities = queryVariables(processInstanceId, newRequest, variableName);
    if (entities.size() > 0) {
      final VariableDto entity = entities.get(0);
      entity.setIsFirst(false); // this was not the original query
      if (request.getSearchAfterOrEqual() != null) {
        //insert at the beginning of the list and remove the last element
        if (request.getPageSize() != null && response.size() == request.getPageSize()) {
          response.remove(response.size() - 1);
        }
        response.add(0, entity);
      } else if (request.getSearchBeforeOrEqual() != null) {
        //insert at the end of the list and remove the first element
        if (request.getPageSize() != null && response.size() == request.getPageSize()) {
          response.remove(0);
        }
        response.add(entity);
      }
    }
  }

  private List<VariableDto> queryVariables(
      final String processInstanceId, VariableRequestDto variableRequest) {
    return queryVariables(processInstanceId, variableRequest, null);
  }

  private List<VariableDto> queryVariables(
      final String processInstanceId, VariableRequestDto request, String varName) {
    Long scopeKey = null;
    if (request.getScopeId() != null) {
      scopeKey = Long.valueOf(request.getScopeId());
    }
    final TermQueryBuilder processInstanceKeyQuery =
        termQuery(VariableTemplate.PROCESS_INSTANCE_KEY, processInstanceId);
    final TermQueryBuilder scopeKeyQuery = termQuery(VariableTemplate.SCOPE_KEY, scopeKey);
    TermQueryBuilder varNameQ = null;
    if (varName != null) {
      varNameQ = termQuery(NAME, varName);
    }

    final ConstantScoreQueryBuilder query =
        constantScoreQuery(joinWithAnd(processInstanceKeyQuery, scopeKeyQuery, varNameQ));

    final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder().query(query);

    applySorting(searchSourceBuilder, request);

    final SearchRequest searchRequest =
        ElasticsearchUtil.createSearchRequest(variableTemplate, ALL)
            .source(searchSourceBuilder);
    try {
      SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);

      List<VariableEntity> variableEntities = ElasticsearchUtil.mapSearchHits(response.getHits().getHits(),
          (sh) -> {
            VariableEntity entity = ElasticsearchUtil.fromSearchHit(sh.getSourceAsString(), objectMapper, VariableEntity.class);
            entity.setSortValues(sh.getSortValues());
            return entity;
          });

      //TODO will be fixed separately
      final Map<String, List<OperationEntity>> operations =
          operationReader.getOperationsPerVariableName(Long.valueOf(processInstanceId), scopeKey);
      final List<VariableDto> variables = VariableDto.createFrom(variableEntities, operations);

      if (variables.size() > 0) {
        if (request.getSearchBefore() != null || request.getSearchBeforeOrEqual() != null) {
          //in this case we were querying for size+1 results
          if (variables.size() <= request.getPageSize()) {
            // last task will be the first in the whole list
            variables.get(variables.size() - 1).setIsFirst(true);
          } else {
            // remove last task
            variables.remove(variables.size() - 1);
          }
          Collections.reverse(variables);
        } else if (request.getSearchAfter() == null && request.getSearchAfterOrEqual() == null) {
          variables.get(0).setIsFirst(true);
        }
      }
      return variables;
    } catch (IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining variables: %s", e.getMessage());
      logger.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  private void applySorting(final SearchSourceBuilder searchSourceBuilder,
      final VariableRequestDto request) {
    final boolean directSorting =
        request.getSearchAfter() != null || request.getSearchAfterOrEqual() != null
            || (request.getSearchBefore() == null && request.getSearchBeforeOrEqual() == null);

    if (directSorting) { //this sorting is also the default one for 1st page
      searchSourceBuilder
          .sort(NAME, SortOrder.ASC);
      if (request.getSearchAfter() != null) {
        searchSourceBuilder.searchAfter(request.getSearchAfter());
      } else if (request.getSearchAfterOrEqual() != null) {
        searchSourceBuilder.searchAfter(request.getSearchAfterOrEqual());
      }
      searchSourceBuilder.size(request.getPageSize());
    } else { //searchBefore != null
      //reverse sorting
      searchSourceBuilder
          .sort(NAME, SortOrder.DESC);
      if (request.getSearchBefore() != null) {
        searchSourceBuilder.searchAfter(request.getSearchBefore());
      } else if (request.getSearchBeforeOrEqual() != null) {
        searchSourceBuilder.searchAfter(request.getSearchBeforeOrEqual());
      }
      searchSourceBuilder.size(request.getPageSize() + 1);
    }
  }

  @Deprecated
  public List<VariableDto> getVariablesOld(Long processInstanceKey, Long scopeKey) {
    final TermQueryBuilder processInstanceKeyQuery = termQuery(VariableTemplate.PROCESS_INSTANCE_KEY, processInstanceKey);
    final TermQueryBuilder scopeKeyQuery = termQuery(VariableTemplate.SCOPE_KEY, scopeKey);

    final ConstantScoreQueryBuilder query = constantScoreQuery(joinWithAnd(processInstanceKeyQuery, scopeKeyQuery));

    final SearchRequest searchRequest = ElasticsearchUtil.createSearchRequest(variableTemplate, ALL)
        .source(new SearchSourceBuilder()
            .query(query)
            .sort(VariableTemplate.NAME, SortOrder.ASC));
    try {
      final List<VariableEntity> variableEntities = scroll(searchRequest, VariableEntity.class);
      final Map<String, List<OperationEntity>> operations = operationReader.getOperationsPerVariableName(processInstanceKey, scopeKey);
      return VariableDto.createFrom(variableEntities, operations);
    } catch (IOException e) {
      final String message = String.format("Exception occurred, while obtaining variables: %s", e.getMessage());
      logger.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.elasticsearch.reader;

import static io.camunda.operate.util.ElasticsearchUtil.QueryType.ALL;
import static io.camunda.operate.util.ElasticsearchUtil.fromSearchHit;
import static io.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static io.camunda.webapps.schema.descriptors.operate.ProcessInstanceDependant.PROCESS_INSTANCE_KEY;
import static io.camunda.webapps.schema.descriptors.operate.template.VariableTemplate.FULL_VALUE;
import static io.camunda.webapps.schema.descriptors.operate.template.VariableTemplate.NAME;
import static io.camunda.webapps.schema.descriptors.operate.template.VariableTemplate.SCOPE_KEY;
import static org.elasticsearch.index.query.QueryBuilders.constantScoreQuery;
import static org.elasticsearch.index.query.QueryBuilders.idsQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.config.operate.OperateProperties;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.webapp.reader.OperationReader;
import io.camunda.operate.webapp.rest.dto.VariableDto;
import io.camunda.operate.webapp.rest.dto.VariableRequestDto;
import io.camunda.operate.webapp.rest.exception.NotFoundException;
import io.camunda.webapps.schema.descriptors.operate.template.VariableTemplate;
import io.camunda.webapps.schema.entities.operate.VariableEntity;
import io.camunda.webapps.schema.entities.operation.OperationEntity;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.ConstantScoreQueryBuilder;
import org.elasticsearch.index.query.IdsQueryBuilder;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(ElasticsearchCondition.class)
@Component
public class VariableReader extends AbstractReader
    implements io.camunda.operate.webapp.reader.VariableReader {

  private static final Logger LOGGER = LoggerFactory.getLogger(VariableReader.class);

  @Autowired
  @Qualifier("operateVariableTemplate")
  private VariableTemplate variableTemplate;

  @Autowired private OperationReader operationReader;

  @Autowired private OperateProperties operateProperties;

  @Override
  public List<VariableDto> getVariables(
      final String processInstanceId, final VariableRequestDto request) {
    final List<VariableDto> response = queryVariables(processInstanceId, request);

    // query one additional instance
    if (request.getSearchAfterOrEqual() != null || request.getSearchBeforeOrEqual() != null) {
      adjustResponse(response, processInstanceId, request);
    }

    if (response.size() > 0
        && (request.getSearchAfter() != null || request.getSearchAfterOrEqual() != null)) {
      final VariableDto firstVar = response.get(0);
      firstVar.setIsFirst(checkVarIsFirst(processInstanceId, request, firstVar.getId()));
    }

    return response;
  }

  @Override
  public VariableDto getVariable(final String id) {
    final IdsQueryBuilder idsQ = idsQuery().addIds(id);
    final SearchRequest searchRequest =
        ElasticsearchUtil.createSearchRequest(variableTemplate, ALL)
            .source(new SearchSourceBuilder().query(idsQ));
    try {
      final SearchResponse response = tenantAwareClient.search(searchRequest);

      if (response.getHits().getTotalHits().value != 1) {
        throw new NotFoundException(String.format("Variable with id %s not found.", id));
      }
      final VariableEntity variableEntity =
          fromSearchHit(
              response.getHits().getHits()[0].getSourceAsString(),
              objectMapper,
              VariableEntity.class);
      return VariableDto.createFrom(
          variableEntity,
          null,
          true,
          operateProperties.getImporter().getVariableSizeThreshold(),
          objectMapper);
    } catch (final IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining variable: %s", e.getMessage());
      LOGGER.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  @Override
  public VariableDto getVariableByName(
      final String processInstanceId, final String scopeId, final String variableName) {

    final TermQueryBuilder processInstanceIdQ = termQuery(PROCESS_INSTANCE_KEY, processInstanceId);
    final TermQueryBuilder scopeIdQ = termQuery(SCOPE_KEY, scopeId);
    final TermQueryBuilder varNameQ = termQuery(NAME, variableName);

    final SearchRequest searchRequest =
        ElasticsearchUtil.createSearchRequest(variableTemplate, ALL)
            .source(
                new SearchSourceBuilder()
                    .query(
                        constantScoreQuery(joinWithAnd(processInstanceIdQ, scopeIdQ, varNameQ))));

    try {
      final SearchResponse response = tenantAwareClient.search(searchRequest);

      if (response.getHits().getTotalHits().value > 0) {
        final VariableEntity variableEntity =
            ElasticsearchUtil.fromSearchHit(
                response.getHits().getHits()[0].getSourceAsString(),
                objectMapper,
                VariableEntity.class);
        return VariableDto.createFrom(
            variableEntity,
            null,
            true,
            operateProperties.getImporter().getVariableSizeThreshold(),
            objectMapper);
      } else {
        return null;
      }
    } catch (final IOException e) {
      final String message =
          String.format(
              "Exception occurred, while obtaining variable for processInstanceId: %s, "
                  + "scopeId: %s, name: %s, error: %s",
              processInstanceId, scopeId, variableName, e.getMessage());
      throw new OperateRuntimeException(message, e);
    }
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
      variableName = (String) request.getSearchAfterOrEqual(objectMapper)[0];
    } else if (request.getSearchBeforeOrEqual() != null) {
      variableName = (String) request.getSearchBeforeOrEqual(objectMapper)[0];
    }

    final VariableRequestDto newRequest =
        request
            .createCopy()
            .setSearchAfter(null)
            .setSearchAfterOrEqual(null)
            .setSearchBefore(null)
            .setSearchBeforeOrEqual(null);

    final List<VariableDto> entities = queryVariables(processInstanceId, newRequest, variableName);
    if (entities.size() > 0) {
      final VariableDto entity = entities.get(0);
      entity.setIsFirst(false); // this was not the original query
      if (request.getSearchAfterOrEqual() != null) {
        // insert at the beginning of the list and remove the last element
        if (request.getPageSize() != null && response.size() == request.getPageSize()) {
          response.remove(response.size() - 1);
        }
        response.add(0, entity);
      } else if (request.getSearchBeforeOrEqual() != null) {
        // insert at the end of the list and remove the first element
        if (request.getPageSize() != null && response.size() == request.getPageSize()) {
          response.remove(0);
        }
        response.add(entity);
      }
    }
  }

  private List<VariableDto> queryVariables(
      final String processInstanceId, final VariableRequestDto variableRequest) {
    return queryVariables(processInstanceId, variableRequest, null);
  }

  private List<VariableDto> queryVariables(
      final String processInstanceId, final VariableRequestDto request, final String varName) {
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

    final SearchSourceBuilder searchSourceBuilder =
        new SearchSourceBuilder().query(query).fetchSource(null, FULL_VALUE);

    applySorting(searchSourceBuilder, request);

    final SearchRequest searchRequest =
        ElasticsearchUtil.createSearchRequest(variableTemplate, ALL).source(searchSourceBuilder);
    try {
      final SearchResponse response = tenantAwareClient.search(searchRequest);

      final List<VariableEntity> variableEntities =
          ElasticsearchUtil.mapSearchHits(
              response.getHits().getHits(),
              (sh) -> {
                final VariableEntity entity =
                    ElasticsearchUtil.fromSearchHit(
                        sh.getSourceAsString(), objectMapper, VariableEntity.class);
                entity.setSortValues(sh.getSortValues());
                return entity;
              });

      final Map<String, List<OperationEntity>> operations =
          operationReader.getUpdateOperationsPerVariableName(
              Long.valueOf(processInstanceId), scopeKey);
      final List<VariableDto> variables =
          VariableDto.createFrom(
              variableEntities,
              operations,
              operateProperties.getImporter().getVariableSizeThreshold(),
              objectMapper);

      if (variables.size() > 0) {
        if (request.getSearchBefore() != null || request.getSearchBeforeOrEqual() != null) {
          // in this case we were querying for size+1 results
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
    } catch (final IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining variables: %s", e.getMessage());
      throw new OperateRuntimeException(message, e);
    }
  }

  private void applySorting(
      final SearchSourceBuilder searchSourceBuilder, final VariableRequestDto request) {
    final boolean directSorting =
        request.getSearchAfter() != null
            || request.getSearchAfterOrEqual() != null
            || (request.getSearchBefore() == null && request.getSearchBeforeOrEqual() == null);

    if (directSorting) { // this sorting is also the default one for 1st page
      searchSourceBuilder.sort(NAME, SortOrder.ASC);
      if (request.getSearchAfter() != null) {
        searchSourceBuilder.searchAfter(request.getSearchAfter(objectMapper));
      } else if (request.getSearchAfterOrEqual() != null) {
        searchSourceBuilder.searchAfter(request.getSearchAfterOrEqual(objectMapper));
      }
      searchSourceBuilder.size(request.getPageSize());
    } else { // searchBefore != null
      // reverse sorting
      searchSourceBuilder.sort(NAME, SortOrder.DESC);
      if (request.getSearchBefore() != null) {
        searchSourceBuilder.searchAfter(request.getSearchBefore(objectMapper));
      } else if (request.getSearchBeforeOrEqual() != null) {
        searchSourceBuilder.searchAfter(request.getSearchBeforeOrEqual(objectMapper));
      }
      searchSourceBuilder.size(request.getPageSize() + 1);
    }
  }
}

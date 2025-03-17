/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.opensearch.reader;

import static io.camunda.operate.store.opensearch.dsl.QueryDSL.*;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.searchRequestBuilder;
import static io.camunda.operate.util.CollectionUtil.toSafeListOfStrings;
import static io.camunda.webapps.schema.descriptors.operate.template.VariableTemplate.FULL_VALUE;
import static io.camunda.webapps.schema.descriptors.operate.template.VariableTemplate.NAME;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.config.operate.OperateProperties;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.webapp.reader.OperationReader;
import io.camunda.operate.webapp.reader.VariableReader;
import io.camunda.operate.webapp.rest.dto.VariableDto;
import io.camunda.operate.webapp.rest.dto.VariableRequestDto;
import io.camunda.operate.webapp.rest.exception.NotFoundException;
import io.camunda.webapps.schema.descriptors.operate.ProcessInstanceDependant;
import io.camunda.webapps.schema.descriptors.operate.template.VariableTemplate;
import io.camunda.webapps.schema.entities.operate.VariableEntity;
import io.camunda.webapps.schema.entities.operation.OperationEntity;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(OpensearchCondition.class)
@Component
public class OpensearchVariableReader implements VariableReader {

  @Autowired private OperateProperties operateProperties;

  @Autowired
  @Qualifier("operateVariableTemplate")
  private VariableTemplate variableTemplate;

  @Autowired private OperationReader operationReader;

  @Autowired private RichOpenSearchClient richOpenSearchClient;

  @Autowired
  @Qualifier("operateObjectMapper")
  private ObjectMapper objectMapper;

  @Override
  public List<VariableDto> getVariables(
      final String processInstanceId, final VariableRequestDto request) {
    final List<VariableDto> response = queryVariables(processInstanceId, request);

    // query one additional instance
    if (request.getSearchAfterOrEqual() != null || request.getSearchBeforeOrEqual() != null) {
      adjustResponse(response, processInstanceId, request);
    }

    if (!response.isEmpty()
        && (request.getSearchAfter() != null || request.getSearchAfterOrEqual() != null)) {
      final VariableDto firstVar = response.get(0);
      firstVar.setIsFirst(checkVarIsFirst(processInstanceId, request, firstVar.getId()));
    }
    return response;
  }

  @Override
  public VariableDto getVariable(final String id) {
    final var searchRequest =
        searchRequestBuilder(variableTemplate).query(withTenantCheck(ids(id)));
    final var hits = richOpenSearchClient.doc().search(searchRequest, VariableEntity.class).hits();
    if (hits.total().value() != 1) {
      throw new NotFoundException(String.format("Variable with id %s not found.", id));
    }
    return toVariableDto(hits.hits().get(0).source());
  }

  @Override
  public VariableDto getVariableByName(
      final String processInstanceId, final String scopeId, final String variableName) {
    final var searchRequest =
        searchRequestBuilder(variableTemplate)
            .query(
                constantScore(
                    withTenantCheck(
                        and(
                            term(ProcessInstanceDependant.PROCESS_INSTANCE_KEY, processInstanceId),
                            term(VariableTemplate.SCOPE_KEY, scopeId),
                            term(VariableTemplate.NAME, variableName)))));
    final var hits = richOpenSearchClient.doc().search(searchRequest, VariableEntity.class).hits();
    if (hits.total().value() > 0) {
      return toVariableDto(hits.hits().get(0).source());
    } else {
      return null;
    }
  }

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
    if (!entities.isEmpty()) {
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
    if (!vars.isEmpty()) {
      return vars.get(0).getId().equals(id);
    } else {
      return false;
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
    final var req =
        searchRequestBuilder(variableTemplate)
            .query(
                constantScore(
                    withTenantCheck(
                        and(
                            term(VariableTemplate.PROCESS_INSTANCE_KEY, processInstanceId),
                            term(VariableTemplate.SCOPE_KEY, scopeKey),
                            (varName != null ? term(NAME, varName) : null)))))
            .source(sourceExclude(FULL_VALUE));
    applySorting(req, request);
    final var response = richOpenSearchClient.doc().search(req, VariableEntity.class);
    final List<VariableEntity> variableEntities =
        response.hits().hits().stream()
            .filter(hit -> hit.source() != null)
            .map(hit -> hit.source().setSortValues(hit.sort().toArray()))
            .toList();

    final Map<String, List<OperationEntity>> operations =
        operationReader.getUpdateOperationsPerVariableName(
            Long.valueOf(processInstanceId), scopeKey);
    final List<VariableDto> variables =
        VariableDto.createFrom(
            variableEntities,
            operations,
            operateProperties.getImporter().getVariableSizeThreshold(),
            objectMapper);

    if (!variables.isEmpty()) {
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
  }

  private void applySorting(
      final SearchRequest.Builder searchRequest, final VariableRequestDto request) {
    final boolean directSorting =
        request.getSearchAfter() != null
            || request.getSearchAfterOrEqual() != null
            || (request.getSearchBefore() == null && request.getSearchBeforeOrEqual() == null);

    if (directSorting) { // this sorting is also the default one for 1st page
      searchRequest.sort(sortOptions(NAME, SortOrder.Asc));
      if (request.getSearchAfter() != null) {
        searchRequest.searchAfter(toSafeListOfStrings(request.getSearchAfter(objectMapper)));
      } else if (request.getSearchAfterOrEqual() != null) {
        searchRequest.searchAfter(toSafeListOfStrings(request.getSearchAfterOrEqual(objectMapper)));
      }
      searchRequest.size(request.getPageSize());
    } else { // searchBefore != null
      // reverse sorting
      searchRequest.sort(sortOptions(NAME, SortOrder.Desc));
      if (request.getSearchBefore() != null) {
        searchRequest.searchAfter(toSafeListOfStrings(request.getSearchBefore(objectMapper)));
      } else if (request.getSearchBeforeOrEqual() != null) {
        searchRequest.searchAfter(
            toSafeListOfStrings(request.getSearchBeforeOrEqual(objectMapper)));
      }
      searchRequest.size(request.getPageSize() + 1);
    }
  }

  private VariableDto toVariableDto(final VariableEntity variableEntity) {
    return VariableDto.createFrom(
        variableEntity,
        null,
        true,
        operateProperties.getImporter().getVariableSizeThreshold(),
        objectMapper);
  }
}

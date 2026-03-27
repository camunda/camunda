/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.adapter;

import io.camunda.gateway.mapping.http.search.SearchQueryRequestMapper;
import io.camunda.gateway.mapping.http.search.SearchQueryResponseMapper;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedProcessDefinitionElementStatisticsQueryStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedProcessDefinitionInstanceStatisticsQueryStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedProcessDefinitionInstanceVersionStatisticsQueryStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedProcessDefinitionMessageSubscriptionStatisticsQueryStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedProcessDefinitionSearchQueryRequestStrictContract;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.ProcessDefinitionServices;
import io.camunda.zeebe.gateway.rest.controller.generated.ProcessDefinitionServiceAdapter;
import io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class DefaultProcessDefinitionServiceAdapter implements ProcessDefinitionServiceAdapter {

  private final ProcessDefinitionServices processDefinitionServices;

  public DefaultProcessDefinitionServiceAdapter(
      final ProcessDefinitionServices processDefinitionServices) {
    this.processDefinitionServices = processDefinitionServices;
  }

  @Override
  public ResponseEntity<Object> searchProcessDefinitions(
      final GeneratedProcessDefinitionSearchQueryRequestStrictContract
          processDefinitionSearchQueryStrict,
      final CamundaAuthentication authentication) {
    return SearchQueryRequestMapper.toProcessDefinitionQueryStrict(
            processDefinitionSearchQueryStrict)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            query -> {
              try {
                final var result = processDefinitionServices.search(query, authentication);
                return ResponseEntity.ok(
                    SearchQueryResponseMapper.toProcessDefinitionSearchQueryResponse(result));
              } catch (final Exception e) {
                return RestErrorMapper.mapErrorToResponse(e);
              }
            });
  }

  @Override
  public ResponseEntity<Object> getProcessDefinition(
      final Long processDefinitionKey, final CamundaAuthentication authentication) {
    try {
      return ResponseEntity.ok()
          .body(
              SearchQueryResponseMapper.toProcessDefinition(
                  processDefinitionServices.getByKey(processDefinitionKey, authentication)));
    } catch (final Exception e) {
      return RestErrorMapper.mapErrorToResponse(e);
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public ResponseEntity<Void> getProcessDefinitionXML(
      final Long processDefinitionKey, final CamundaAuthentication authentication) {
    try {
      return (ResponseEntity<Void>)
          (ResponseEntity<?>)
              processDefinitionServices
                  .getProcessDefinitionXml(processDefinitionKey, authentication)
                  .map(
                      s ->
                          ResponseEntity.ok()
                              .contentType(
                                  new MediaType(MediaType.TEXT_XML, StandardCharsets.UTF_8))
                              .body(s))
                  .orElseGet(() -> ResponseEntity.status(HttpStatus.NO_CONTENT).build());
    } catch (final Exception e) {
      return RestErrorMapper.mapErrorToResponse(e);
    }
  }

  @Override
  public ResponseEntity<Object> getStartProcessForm(
      final Long processDefinitionKey, final CamundaAuthentication authentication) {
    try {
      return processDefinitionServices
          .getProcessDefinitionStartForm(processDefinitionKey, authentication)
          .<Object>map(SearchQueryResponseMapper::toFormItem)
          .<ResponseEntity<Object>>map(form -> ResponseEntity.ok(form))
          .orElseGet(() -> ResponseEntity.noContent().build());
    } catch (final Exception e) {
      return RestErrorMapper.mapErrorToResponse(e);
    }
  }

  @Override
  public ResponseEntity<Object> getProcessDefinitionStatistics(
      final Long processDefinitionKey,
      final GeneratedProcessDefinitionElementStatisticsQueryStrictContract queryStrict,
      final CamundaAuthentication authentication) {
    return SearchQueryRequestMapper.toProcessDefinitionStatisticsQuery(
            processDefinitionKey, queryStrict)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            filter -> {
              try {
                final var result =
                    processDefinitionServices.elementStatistics(filter, authentication);
                return ResponseEntity.ok(
                    SearchQueryResponseMapper.toProcessDefinitionElementStatisticsResult(result));
              } catch (final Exception e) {
                return RestErrorMapper.mapErrorToResponse(e);
              }
            });
  }

  @Override
  public ResponseEntity<Object> getProcessDefinitionMessageSubscriptionStatistics(
      final GeneratedProcessDefinitionMessageSubscriptionStatisticsQueryStrictContract queryStrict,
      final CamundaAuthentication authentication) {
    return SearchQueryRequestMapper.toProcessDefinitionMessageSubscriptionStatisticsQuery(
            queryStrict)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            serviceQuery -> {
              try {
                final var result =
                    processDefinitionServices.getProcessDefinitionMessageSubscriptionStatistics(
                        serviceQuery, authentication);
                return ResponseEntity.ok(
                    SearchQueryResponseMapper
                        .toProcessDefinitionMessageSubscriptionStatisticsQueryResponse(result));
              } catch (final Exception e) {
                return RestErrorMapper.mapErrorToResponse(e);
              }
            });
  }

  @Override
  public ResponseEntity<Object> getProcessDefinitionInstanceStatistics(
      final GeneratedProcessDefinitionInstanceStatisticsQueryStrictContract queryStrict,
      final CamundaAuthentication authentication) {
    return SearchQueryRequestMapper.toProcessDefinitionInstanceStatisticsQuery(queryStrict)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            serviceQuery -> {
              try {
                final var result =
                    processDefinitionServices.getProcessDefinitionInstanceStatistics(
                        serviceQuery, authentication);
                return ResponseEntity.ok(
                    SearchQueryResponseMapper.toProcessInstanceStatisticsQueryResult(result));
              } catch (final Exception e) {
                return RestErrorMapper.mapErrorToResponse(e);
              }
            });
  }

  @Override
  public ResponseEntity<Object> getProcessDefinitionInstanceVersionStatistics(
      final GeneratedProcessDefinitionInstanceVersionStatisticsQueryStrictContract queryStrict,
      final CamundaAuthentication authentication) {
    return SearchQueryRequestMapper.toProcessDefinitionInstanceVersionStatisticsQuery(queryStrict)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            serviceQuery -> {
              try {
                final var result =
                    processDefinitionServices.searchProcessDefinitionInstanceVersionStatistics(
                        serviceQuery, authentication);
                return ResponseEntity.ok(
                    SearchQueryResponseMapper.toProcessInstanceVersionStatisticsQueryResult(
                        result));
              } catch (final Exception e) {
                return RestErrorMapper.mapErrorToResponse(e);
              }
            });
  }
}

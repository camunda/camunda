/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper.mapErrorToResponse;

import io.camunda.gateway.mapping.http.search.SearchQueryRequestMapper;
import io.camunda.gateway.mapping.http.search.SearchQueryResponseMapper;
import io.camunda.gateway.protocol.model.FormResult;
import io.camunda.gateway.protocol.model.ProcessDefinitionElementStatisticsQuery;
import io.camunda.gateway.protocol.model.ProcessDefinitionElementStatisticsQueryResult;
import io.camunda.gateway.protocol.model.ProcessDefinitionInstanceStatisticsQuery;
import io.camunda.gateway.protocol.model.ProcessDefinitionInstanceStatisticsQueryResult;
import io.camunda.gateway.protocol.model.ProcessDefinitionInstanceVersionStatisticsQuery;
import io.camunda.gateway.protocol.model.ProcessDefinitionInstanceVersionStatisticsQueryResult;
import io.camunda.gateway.protocol.model.ProcessDefinitionMessageSubscriptionStatisticsQuery;
import io.camunda.gateway.protocol.model.ProcessDefinitionMessageSubscriptionStatisticsQueryResult;
import io.camunda.gateway.protocol.model.ProcessDefinitionSearchQuery;
import io.camunda.gateway.protocol.model.ProcessDefinitionSearchQueryResult;
import io.camunda.search.filter.ProcessDefinitionStatisticsFilter;
import io.camunda.search.query.ProcessDefinitionQuery;
import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.service.registry.ServiceRegistry;
import io.camunda.zeebe.gateway.rest.annotation.CamundaGetMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import io.camunda.zeebe.gateway.rest.annotation.PhysicalTenantId;
import io.camunda.zeebe.gateway.rest.annotation.RequiresSecondaryStorage;
import io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestController
@RequestMapping("/v2/process-definitions")
public class ProcessDefinitionController {

  private final ServiceRegistry serviceRegistry;
  private final CamundaAuthenticationProvider authenticationProvider;

  public ProcessDefinitionController(
      final ServiceRegistry serviceRegistry,
      final CamundaAuthenticationProvider authenticationProvider) {
    this.serviceRegistry = serviceRegistry;
    this.authenticationProvider = authenticationProvider;
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/search")
  public ResponseEntity<ProcessDefinitionSearchQueryResult> searchProcessDefinitions(
      @PhysicalTenantId final String physicalTenantId,
      @RequestBody(required = false) final ProcessDefinitionSearchQuery query) {
    return SearchQueryRequestMapper.toProcessDefinitionQuery(query)
        .fold(RestErrorMapper::mapProblemToResponse, q -> search(physicalTenantId, q));
  }

  private ResponseEntity<ProcessDefinitionSearchQueryResult> search(
      final String physicalTenantId, final ProcessDefinitionQuery query) {
    final var processDefinitionServices =
        serviceRegistry.processDefinitionServices(physicalTenantId);
    try {
      final var result =
          processDefinitionServices.search(
              query, authenticationProvider.getCamundaAuthentication());
      return ResponseEntity.ok(
          SearchQueryResponseMapper.toProcessDefinitionSearchQueryResponse(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  @RequiresSecondaryStorage
  @CamundaGetMapping(path = "/{processDefinitionKey}")
  public ResponseEntity<Object> getByKey(
      @PhysicalTenantId final String physicalTenantId,
      @PathVariable("processDefinitionKey") final Long processDefinitionKey) {
    try {
      // Success case: Return the left side with the ProcessDefinitionEntity wrapped in
      // ResponseEntity
      return ResponseEntity.ok()
          .body(
              SearchQueryResponseMapper.toProcessDefinition(
                  serviceRegistry
                      .processDefinitionServices(physicalTenantId)
                      .getByKey(
                          processDefinitionKey,
                          authenticationProvider.getCamundaAuthentication())));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  @RequiresSecondaryStorage
  @CamundaGetMapping(
      path = "/{processDefinitionKey}/xml",
      produces = {MediaType.TEXT_XML_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE})
  public ResponseEntity<String> getProcessDefinitionXml(
      @PhysicalTenantId final String physicalTenantId,
      @PathVariable("processDefinitionKey") final long processDefinitionKey) {
    try {
      return serviceRegistry
          .processDefinitionServices(physicalTenantId)
          .getProcessDefinitionXml(
              processDefinitionKey, authenticationProvider.getCamundaAuthentication())
          .map(
              s ->
                  ResponseEntity.ok()
                      .contentType(new MediaType(MediaType.TEXT_XML, StandardCharsets.UTF_8))
                      .body(s))
          .orElseGet(() -> ResponseEntity.status(HttpStatus.NO_CONTENT).build());
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  @RequiresSecondaryStorage
  @CamundaGetMapping(path = "/{processDefinitionKey}/form")
  public ResponseEntity<FormResult> getStartProcessForm(
      @PhysicalTenantId final String physicalTenantId,
      @PathVariable("processDefinitionKey") final long processDefinitionKey) {
    try {
      return serviceRegistry
          .processDefinitionServices(physicalTenantId)
          .getProcessDefinitionStartForm(
              processDefinitionKey, authenticationProvider.getCamundaAuthentication())
          .map(SearchQueryResponseMapper::toFormItem)
          .map(s -> ResponseEntity.ok().body(s))
          .orElseGet(() -> ResponseEntity.noContent().build());
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/{processDefinitionKey}/statistics/element-instances")
  public ResponseEntity<ProcessDefinitionElementStatisticsQueryResult> elementStatistics(
      @PhysicalTenantId final String physicalTenantId,
      @PathVariable("processDefinitionKey") final long processDefinitionKey,
      @RequestBody(required = false) final ProcessDefinitionElementStatisticsQuery query) {
    return SearchQueryRequestMapper.toProcessDefinitionStatisticsQuery(processDefinitionKey, query)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            filter -> elementStatistics(physicalTenantId, filter));
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/statistics/message-subscriptions")
  public ResponseEntity<ProcessDefinitionMessageSubscriptionStatisticsQueryResult>
      messageSubscriptionStatistics(
          @PhysicalTenantId final String physicalTenantId,
          @RequestBody(required = false)
              final ProcessDefinitionMessageSubscriptionStatisticsQuery searchRequest) {
    return SearchQueryRequestMapper.toProcessDefinitionMessageSubscriptionStatisticsQuery(
            searchRequest)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            query -> getMessageSubscriptionStatistics(physicalTenantId, query));
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/statistics/process-instances")
  public ResponseEntity<ProcessDefinitionInstanceStatisticsQueryResult> processInstanceStatistics(
      @PhysicalTenantId final String physicalTenantId,
      @RequestBody(required = false) final ProcessDefinitionInstanceStatisticsQuery query) {
    return SearchQueryRequestMapper.toProcessDefinitionInstanceStatisticsQuery(query)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            q -> getProcessDefinitionInstanceStatistics(physicalTenantId, q));
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/statistics/process-instances-by-version")
  public ResponseEntity<ProcessDefinitionInstanceVersionStatisticsQueryResult>
      processInstanceVersionStatistics(
          @PhysicalTenantId final String physicalTenantId,
          @RequestBody() final ProcessDefinitionInstanceVersionStatisticsQuery query) {
    return SearchQueryRequestMapper.toProcessDefinitionInstanceVersionStatisticsQuery(query)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            q -> searchProcessDefinitionInstanceVersionStatistics(physicalTenantId, q));
  }

  private ResponseEntity<ProcessDefinitionElementStatisticsQueryResult> elementStatistics(
      final String physicalTenantId, final ProcessDefinitionStatisticsFilter filter) {
    final var processDefinitionServices =
        serviceRegistry.processDefinitionServices(physicalTenantId);
    try {
      final var result =
          processDefinitionServices.elementStatistics(
              filter, authenticationProvider.getCamundaAuthentication());
      return ResponseEntity.ok(
          SearchQueryResponseMapper.toProcessDefinitionElementStatisticsResult(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  private ResponseEntity<ProcessDefinitionInstanceStatisticsQueryResult>
      getProcessDefinitionInstanceStatistics(
          final String physicalTenantId,
          final io.camunda.search.query.ProcessDefinitionInstanceStatisticsQuery query) {
    final var processDefinitionServices =
        serviceRegistry.processDefinitionServices(physicalTenantId);
    try {
      final var result =
          processDefinitionServices.getProcessDefinitionInstanceStatistics(
              query, authenticationProvider.getCamundaAuthentication());
      return ResponseEntity.ok(
          SearchQueryResponseMapper.toProcessInstanceStatisticsQueryResult(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  private ResponseEntity<ProcessDefinitionMessageSubscriptionStatisticsQueryResult>
      getMessageSubscriptionStatistics(
          final String physicalTenantId,
          final io.camunda.search.query.ProcessDefinitionMessageSubscriptionStatisticsQuery query) {
    final var processDefinitionServices =
        serviceRegistry.processDefinitionServices(physicalTenantId);
    try {
      final var result =
          processDefinitionServices.getProcessDefinitionMessageSubscriptionStatistics(
              query, authenticationProvider.getCamundaAuthentication());
      return ResponseEntity.ok(
          SearchQueryResponseMapper.toProcessDefinitionMessageSubscriptionStatisticsQueryResponse(
              result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  private ResponseEntity<ProcessDefinitionInstanceVersionStatisticsQueryResult>
      searchProcessDefinitionInstanceVersionStatistics(
          final String physicalTenantId,
          final io.camunda.search.query.ProcessDefinitionInstanceVersionStatisticsQuery query) {
    final var processDefinitionServices =
        serviceRegistry.processDefinitionServices(physicalTenantId);
    try {
      final var result =
          processDefinitionServices.searchProcessDefinitionInstanceVersionStatistics(
              query, authenticationProvider.getCamundaAuthentication());
      return ResponseEntity.ok(
          SearchQueryResponseMapper.toProcessInstanceVersionStatisticsQueryResult(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }
}

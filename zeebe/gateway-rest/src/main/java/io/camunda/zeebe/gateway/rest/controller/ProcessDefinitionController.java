/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper.mapErrorToResponse;

import io.camunda.search.filter.ProcessDefinitionStatisticsFilter;
import io.camunda.search.query.ProcessDefinitionQuery;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.service.FormServices;
import io.camunda.service.ProcessDefinitionServices;
import io.camunda.zeebe.gateway.protocol.rest.FormResult;
import io.camunda.zeebe.gateway.protocol.rest.ProcessDefinitionElementStatisticsQuery;
import io.camunda.zeebe.gateway.protocol.rest.ProcessDefinitionElementStatisticsQueryResult;
import io.camunda.zeebe.gateway.protocol.rest.ProcessDefinitionInstanceStatisticsQuery;
import io.camunda.zeebe.gateway.protocol.rest.ProcessDefinitionInstanceStatisticsQueryResult;
import io.camunda.zeebe.gateway.protocol.rest.ProcessDefinitionSearchQuery;
import io.camunda.zeebe.gateway.protocol.rest.ProcessDefinitionSearchQueryResult;
import io.camunda.zeebe.gateway.rest.annotation.CamundaGetMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import io.camunda.zeebe.gateway.rest.annotation.RequiresSecondaryStorage;
import io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper;
import io.camunda.zeebe.gateway.rest.mapper.search.SearchQueryRequestMapper;
import io.camunda.zeebe.gateway.rest.mapper.search.SearchQueryResponseMapper;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestController
@RequiresSecondaryStorage
@RequestMapping("/v2/process-definitions")
public class ProcessDefinitionController {

  private final ProcessDefinitionServices processDefinitionServices;
  private final FormServices formServices;
  private final CamundaAuthenticationProvider authenticationProvider;

  public ProcessDefinitionController(
      final ProcessDefinitionServices processDefinitionServices,
      final FormServices formServices,
      final CamundaAuthenticationProvider authenticationProvider) {
    this.processDefinitionServices = processDefinitionServices;
    this.formServices = formServices;
    this.authenticationProvider = authenticationProvider;
  }

  @CamundaPostMapping(path = "/search")
  public ResponseEntity<ProcessDefinitionSearchQueryResult> searchProcessDefinitions(
      @RequestBody(required = false) final ProcessDefinitionSearchQuery query) {
    return SearchQueryRequestMapper.toProcessDefinitionQuery(query)
        .fold(RestErrorMapper::mapProblemToResponse, this::search);
  }

  private ResponseEntity<ProcessDefinitionSearchQueryResult> search(
      final ProcessDefinitionQuery query) {
    try {
      final var result =
          processDefinitionServices
              .withAuthentication(authenticationProvider.getCamundaAuthentication())
              .search(query);
      return ResponseEntity.ok(
          SearchQueryResponseMapper.toProcessDefinitionSearchQueryResponse(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  @CamundaGetMapping(path = "/{processDefinitionKey}")
  public ResponseEntity<Object> getByKey(
      @PathVariable("processDefinitionKey") final Long processDefinitionKey) {
    try {
      // Success case: Return the left side with the ProcessDefinitionEntity wrapped in
      // ResponseEntity
      return ResponseEntity.ok()
          .body(
              SearchQueryResponseMapper.toProcessDefinition(
                  processDefinitionServices
                      .withAuthentication(authenticationProvider.getCamundaAuthentication())
                      .getByKey(processDefinitionKey)));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  @CamundaGetMapping(
      path = "/{processDefinitionKey}/xml",
      produces = {MediaType.TEXT_XML_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE})
  public ResponseEntity<String> getProcessDefinitionXml(
      @PathVariable("processDefinitionKey") final long processDefinitionKey) {
    try {
      return processDefinitionServices
          .withAuthentication(authenticationProvider.getCamundaAuthentication())
          .getProcessDefinitionXml(processDefinitionKey)
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

  @CamundaGetMapping(path = "/{processDefinitionKey}/form")
  public ResponseEntity<FormResult> getStartProcessForm(
      @PathVariable("processDefinitionKey") final long processDefinitionKey) {
    try {
      return processDefinitionServices
          .withAuthentication(authenticationProvider.getCamundaAuthentication())
          .getProcessDefinitionStartForm(processDefinitionKey)
          .map(SearchQueryResponseMapper::toFormItem)
          .map(s -> ResponseEntity.ok().body(s))
          .orElseGet(() -> ResponseEntity.noContent().build());
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  @CamundaPostMapping(path = "/{processDefinitionKey}/statistics/element-instances")
  public ResponseEntity<ProcessDefinitionElementStatisticsQueryResult> elementStatistics(
      @PathVariable("processDefinitionKey") final long processDefinitionKey,
      @RequestBody(required = false) final ProcessDefinitionElementStatisticsQuery query) {
    return SearchQueryRequestMapper.toProcessDefinitionStatisticsQuery(processDefinitionKey, query)
        .fold(RestErrorMapper::mapProblemToResponse, this::elementStatistics);
  }

  @CamundaPostMapping(path = "/statistics/process-instances")
  public ResponseEntity<ProcessDefinitionInstanceStatisticsQueryResult> processInstanceStatistics(
      @RequestBody(required = false) final ProcessDefinitionInstanceStatisticsQuery query) {
    return SearchQueryRequestMapper.toProcessDefinitionInstanceStatisticsQuery(query)
        .fold(RestErrorMapper::mapProblemToResponse, this::getProcessDefinitionInstanceStatistics);
  }

  private ResponseEntity<ProcessDefinitionElementStatisticsQueryResult> elementStatistics(
      final ProcessDefinitionStatisticsFilter filter) {
    try {
      final var result =
          processDefinitionServices
              .withAuthentication(authenticationProvider.getCamundaAuthentication())
              .elementStatistics(filter);
      return ResponseEntity.ok(
          SearchQueryResponseMapper.toProcessDefinitionElementStatisticsResult(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  private ResponseEntity<ProcessDefinitionInstanceStatisticsQueryResult>
      getProcessDefinitionInstanceStatistics(
          final io.camunda.search.query.ProcessDefinitionInstanceStatisticsQuery query) {
    try {
      final var result =
          processDefinitionServices
              .withAuthentication(authenticationProvider.getCamundaAuthentication())
              .getProcessDefinitionInstanceStatistics(query);
      return ResponseEntity.ok(
          SearchQueryResponseMapper.toProcessInstanceStatisticsQueryResult(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }
}

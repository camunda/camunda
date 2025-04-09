/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static io.camunda.zeebe.gateway.rest.RestErrorMapper.mapErrorToResponse;

import io.camunda.search.entities.ProcessDefinitionEntity;
import io.camunda.search.filter.ProcessDefinitionStatisticsFilter;
import io.camunda.search.query.ProcessDefinitionQuery;
import io.camunda.service.FormServices;
import io.camunda.service.ProcessDefinitionServices;
import io.camunda.zeebe.gateway.protocol.rest.FormResult;
import io.camunda.zeebe.gateway.protocol.rest.ProcessDefinitionElementStatisticsQuery;
import io.camunda.zeebe.gateway.protocol.rest.ProcessDefinitionElementStatisticsQueryResult;
import io.camunda.zeebe.gateway.protocol.rest.ProcessDefinitionSearchQuery;
import io.camunda.zeebe.gateway.protocol.rest.ProcessDefinitionSearchQueryResult;
import io.camunda.zeebe.gateway.rest.RequestMapper;
import io.camunda.zeebe.gateway.rest.RestErrorMapper;
import io.camunda.zeebe.gateway.rest.SearchQueryRequestMapper;
import io.camunda.zeebe.gateway.rest.SearchQueryResponseMapper;
import io.camunda.zeebe.gateway.rest.annotation.CamundaGetMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
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

  private final ProcessDefinitionServices processDefinitionServices;
  private final FormServices formServices;

  public ProcessDefinitionController(
      final ProcessDefinitionServices processDefinitionServices, final FormServices formServices) {
    this.processDefinitionServices = processDefinitionServices;
    this.formServices = formServices;
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
              .withAuthentication(RequestMapper.getAuthentication())
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
                      .withAuthentication(RequestMapper.getAuthentication())
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
          .withAuthentication(RequestMapper.getAuthentication())
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
      final ProcessDefinitionEntity processDefinition =
          processDefinitionServices
              .withAuthentication(RequestMapper.getAuthentication())
              .getByKey(processDefinitionKey);

      if (processDefinition.formId() != null) {
        return ResponseEntity.ok()
            .body(
                SearchQueryResponseMapper.toFormItem(
                    formServices
                        .withAuthentication(RequestMapper.getAuthentication())
                        .getLatestVersionByFormId(processDefinition.formId())
                        .get()));
      } else {
        return ResponseEntity.noContent().build();
      }
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

  private ResponseEntity<ProcessDefinitionElementStatisticsQueryResult> elementStatistics(
      final ProcessDefinitionStatisticsFilter filter) {
    try {
      final var result =
          processDefinitionServices
              .withAuthentication(RequestMapper.getAuthentication())
              .elementStatistics(filter);
      return ResponseEntity.ok(SearchQueryResponseMapper.toProcessElementStatisticsQueryResult(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }
}

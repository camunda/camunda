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
import io.camunda.search.query.ProcessDefinitionQuery;
import io.camunda.service.FormServices;
import io.camunda.service.ProcessDefinitionServices;
import io.camunda.zeebe.gateway.protocol.rest.FormItem;
import io.camunda.zeebe.gateway.protocol.rest.ProcessDefinitionSearchQueryRequest;
import io.camunda.zeebe.gateway.protocol.rest.ProcessDefinitionSearchQueryResponse;
import io.camunda.zeebe.gateway.rest.RequestMapper;
import io.camunda.zeebe.gateway.rest.RestErrorMapper;
import io.camunda.zeebe.gateway.rest.SearchQueryRequestMapper;
import io.camunda.zeebe.gateway.rest.SearchQueryResponseMapper;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestQueryController
@RequestMapping("/v2/process-definitions")
public class ProcessDefinitionQueryController {

  private final ProcessDefinitionServices processDefinitionServices;
  private final FormServices formServices;

  public ProcessDefinitionQueryController(
      final ProcessDefinitionServices processDefinitionServices, final FormServices formServices) {
    this.processDefinitionServices = processDefinitionServices;
    this.formServices = formServices;
  }

  @PostMapping(
      path = "/search",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<ProcessDefinitionSearchQueryResponse> searchProcessDefinitions(
      @RequestBody(required = false) final ProcessDefinitionSearchQueryRequest query) {
    return SearchQueryRequestMapper.toProcessDefinitionQuery(query)
        .fold(RestErrorMapper::mapProblemToResponse, this::search);
  }

  private ResponseEntity<ProcessDefinitionSearchQueryResponse> search(
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

  @GetMapping(
      path = "/{processDefinitionKey}",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE})
  public ResponseEntity<Object> getByKey(
      @PathVariable("processDefinitionKey") final Long processDefinitionKey) {
    try {
      // Success case: Return the left side with the ProcessDefinitionEntity wrapped in
      // ResponseEntity
      return ResponseEntity.ok()
          .body(
              SearchQueryResponseMapper.toProcessDefinition(
                  processDefinitionServices.getByKey(processDefinitionKey)));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  @GetMapping(
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

  @GetMapping(
      path = "/{processDefinitionKey}/form",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE})
  public ResponseEntity<FormItem> getStartProcessForm(
      @PathVariable("processDefinitionKey") final long processDefinitionKey) {
    try {
      final ProcessDefinitionEntity processDefinition =
          processDefinitionServices.getByKey(processDefinitionKey);

      if (processDefinition.formId() != null) {
        return ResponseEntity.ok()
            .body(
                SearchQueryResponseMapper.toFormItem(
                    formServices.getLatestVersionByFormId(processDefinition.formId()).get()));
      } else {
        return ResponseEntity.noContent().build();
      }
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }
}

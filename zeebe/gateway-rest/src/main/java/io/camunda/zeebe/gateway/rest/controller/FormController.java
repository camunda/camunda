/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static io.camunda.zeebe.gateway.rest.Loggers.REST_LOGGER;

import io.camunda.service.FormServices;
import io.camunda.service.exception.NotFoundException;
import io.camunda.zeebe.gateway.protocol.rest.FormItem;
import io.camunda.zeebe.gateway.rest.RestErrorMapper;
import io.camunda.zeebe.gateway.rest.SearchQueryResponseMapper;
import io.camunda.zeebe.protocol.record.RejectionType;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestQueryController
@RequestMapping("/v2/forms")
public class FormController {
  @Autowired private FormServices formServices;

  @GetMapping(
      path = "/{formKey}",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE})
  public ResponseEntity<FormItem> getByKey(@PathVariable("formKey") final Long formKey) {
    try {
      return ResponseEntity.ok()
          .contentType(new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8))
          .body(SearchQueryResponseMapper.toFormItem(formServices.getByKey(formKey)));
    } catch (final NotFoundException nfe) {
      final var problemDetail =
          RestErrorMapper.createProblemDetail(
              HttpStatus.NOT_FOUND, nfe.getMessage(), RejectionType.NOT_FOUND.name());
      return RestErrorMapper.mapProblemToResponse(problemDetail);
    } catch (final Exception e) {
      REST_LOGGER.warn("An exception occurred in get Form by key.", e);
      final var problemDetail =
          RestErrorMapper.createProblemDetail(
              HttpStatus.INTERNAL_SERVER_ERROR,
              e.getMessage(),
              "Failed to execute Get Form by key.");
      return RestErrorMapper.mapProblemToResponse(problemDetail);
    }
  }
}

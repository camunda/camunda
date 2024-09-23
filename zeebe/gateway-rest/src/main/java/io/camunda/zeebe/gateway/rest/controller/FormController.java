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
import io.camunda.zeebe.gateway.protocol.rest.FormItem;
import io.camunda.zeebe.gateway.rest.RestErrorMapper;
import io.camunda.zeebe.gateway.rest.SearchQueryResponseMapper;
import org.springframework.beans.factory.annotation.Autowired;
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
          .body(SearchQueryResponseMapper.toFormItem(formServices.getByKey(formKey)));
    } catch (final Exception exc) {
      REST_LOGGER.warn("An exception occurred in get Form by key.", exc);
      final var problemDetail =
          RestErrorMapper.mapErrorToProblem(exc, RestErrorMapper.DEFAULT_REJECTION_MAPPER);
      return RestErrorMapper.mapProblemToResponse(problemDetail);
    }
  }
}

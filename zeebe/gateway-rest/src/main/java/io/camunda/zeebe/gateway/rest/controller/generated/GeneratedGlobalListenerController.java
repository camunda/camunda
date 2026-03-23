/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 */
package io.camunda.zeebe.gateway.rest.controller.generated;

import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedCreateGlobalTaskListenerRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedGlobalTaskListenerSearchQueryRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedUpdateGlobalTaskListenerRequestStrictContract;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.zeebe.gateway.rest.controller.CamundaRestController;
import jakarta.annotation.Generated;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@CamundaRestController
@RequestMapping("/v2")
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public class GeneratedGlobalListenerController {

  private final GlobalListenerServiceAdapter serviceAdapter;
  private final CamundaAuthenticationProvider authenticationProvider;

  public GeneratedGlobalListenerController(
      final GlobalListenerServiceAdapter serviceAdapter,
      final CamundaAuthenticationProvider authenticationProvider) {
    this.serviceAdapter = serviceAdapter;
    this.authenticationProvider = authenticationProvider;
  }

  @RequestMapping(
      method = RequestMethod.POST,
      value = "/global-task-listeners",
      consumes = {"application/json"},
      produces = {"application/json", "application/problem+json"})
  public ResponseEntity<Object> createGlobalTaskListener(
      @RequestBody
          final GeneratedCreateGlobalTaskListenerRequestStrictContract
              createGlobalTaskListenerRequest) {
    final var authentication = authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.createGlobalTaskListener(createGlobalTaskListenerRequest, authentication);
  }

  @RequestMapping(
      method = RequestMethod.GET,
      value = "/global-task-listeners/{id}",
      produces = {"application/json", "application/problem+json"})
  public ResponseEntity<Object> getGlobalTaskListener(@PathVariable("id") final String id) {
    final var authentication = authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.getGlobalTaskListener(id, authentication);
  }

  @RequestMapping(
      method = RequestMethod.PUT,
      value = "/global-task-listeners/{id}",
      consumes = {"application/json"},
      produces = {"application/json", "application/problem+json"})
  public ResponseEntity<Object> updateGlobalTaskListener(
      @PathVariable("id") final String id,
      @RequestBody
          final GeneratedUpdateGlobalTaskListenerRequestStrictContract
              updateGlobalTaskListenerRequest) {
    final var authentication = authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.updateGlobalTaskListener(
        id, updateGlobalTaskListenerRequest, authentication);
  }

  @RequestMapping(
      method = RequestMethod.DELETE,
      value = "/global-task-listeners/{id}",
      produces = {"application/json", "application/problem+json"})
  public ResponseEntity<Void> deleteGlobalTaskListener(@PathVariable("id") final String id) {
    final var authentication = authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.deleteGlobalTaskListener(id, authentication);
  }

  @RequestMapping(
      method = RequestMethod.POST,
      value = "/global-task-listeners/search",
      consumes = {"application/json"},
      produces = {"application/json", "application/problem+json"})
  public ResponseEntity<Object> searchGlobalTaskListeners(
      @RequestBody
          final GeneratedGlobalTaskListenerSearchQueryRequestStrictContract
              globalTaskListenerSearchQueryRequest) {
    final var authentication = authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.searchGlobalTaskListeners(
        globalTaskListenerSearchQueryRequest, authentication);
  }
}

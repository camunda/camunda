/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.usermanagement;

import io.camunda.zeebe.gateway.protocol.rest.RoleRequest;
import io.camunda.zeebe.gateway.protocol.rest.SearchQueryRequest;
import io.camunda.zeebe.gateway.rest.controller.CamundaRestController;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestController
@RequestMapping("/v2/roles")
public class RoleController {
  @PostMapping(
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Object> createRole(@RequestBody final RoleRequest roleRequest) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @DeleteMapping(path = "/{id}")
  public ResponseEntity<Object> deleteRoleById(@PathVariable("id") final String roleName) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @GetMapping(
      path = "/{id}",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE})
  public ResponseEntity<Object> getRoleById(@PathVariable("id") final String roleName) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @PostMapping(
      path = "/search",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Object> findAllRoles(
      @RequestBody(required = false) final SearchQueryRequest searchQueryRequest) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @PutMapping(
      path = "/{id}",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Object> updateRole(
      @PathVariable("id") final String roleName, @RequestBody final RoleRequest roleRequest) {
    throw new UnsupportedOperationException("Not implemented yet");
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.usermanagement;

import io.camunda.zeebe.gateway.protocol.rest.AssignUserToGroupRequest;
import io.camunda.zeebe.gateway.protocol.rest.CamundaGroupRequest;
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
@RequestMapping("/v2/groups")
public class GroupController {

  @PostMapping(
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Object> createGroup(@RequestBody final CamundaGroupRequest groupRequest) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @DeleteMapping(path = "/{id}")
  public ResponseEntity<Object> deleteGroup(@PathVariable(name = "id") final Long groupId) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @GetMapping(
      path = "/{id}",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE})
  public ResponseEntity<Object> findGroupById(@PathVariable(name = "id") final Long groupId) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @PostMapping(
      path = "/search",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Object> findAllGroups(
      @RequestBody(required = false) final SearchQueryRequest searchQueryRequest) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @PutMapping(
      path = "/{id}",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Object> updateGroup(
      @PathVariable(name = "id") final Long groupId,
      @RequestBody final CamundaGroupRequest groupRequest) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @PostMapping(
      path = "/{id}/users",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Object> assignUserToGroup(
      @PathVariable(name = "id") final Long groupId,
      @RequestBody final AssignUserToGroupRequest assignRequest) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @DeleteMapping(path = "/{id}/users/{userId}")
  public ResponseEntity<Object> removeUserFromGroup(
      @PathVariable(name = "id") final Long groupId, @PathVariable final Long userId) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @PostMapping(
      path = "/{id}/users/search",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Object> findAllUsersOfGroup(
      @PathVariable(name = "id") final Long groupId,
      @RequestBody(required = false) final SearchQueryRequest searchQueryRequest) {
    throw new UnsupportedOperationException("Not implemented yet");
  }
}

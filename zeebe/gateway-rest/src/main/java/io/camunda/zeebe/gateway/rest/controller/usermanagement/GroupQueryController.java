/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.usermanagement;

import io.camunda.service.GroupServices;
import io.camunda.zeebe.gateway.rest.RestErrorMapper;
import io.camunda.zeebe.gateway.rest.SearchQueryResponseMapper;
import io.camunda.zeebe.gateway.rest.controller.CamundaRestController;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestController
@RequestMapping("/v2/groups")
public class GroupQueryController {

  private final GroupServices groupServices;

  public GroupQueryController(final GroupServices groupServices) {
    this.groupServices = groupServices;
  }

  @GetMapping(
      path = "/{groupKey}",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE})
  public ResponseEntity<Object> getGroup(@PathVariable final long groupKey) {
    try {
      return ResponseEntity.ok()
          .body(SearchQueryResponseMapper.toGroup(groupServices.getGroup(groupKey)));
    } catch (final Exception exception) {
      return RestErrorMapper.mapErrorToResponse(exception);
    }
  }
}

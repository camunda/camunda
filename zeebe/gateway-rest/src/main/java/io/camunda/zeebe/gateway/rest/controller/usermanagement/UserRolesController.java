/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.usermanagement;

import io.camunda.search.query.RoleQuery;
import io.camunda.service.RoleServices;
import io.camunda.zeebe.gateway.protocol.rest.RoleSearchQueryRequest;
import io.camunda.zeebe.gateway.protocol.rest.RoleSearchQueryResult;
import io.camunda.zeebe.gateway.rest.RequestMapper;
import io.camunda.zeebe.gateway.rest.RestErrorMapper;
import io.camunda.zeebe.gateway.rest.SearchQueryRequestMapper;
import io.camunda.zeebe.gateway.rest.SearchQueryResponseMapper;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import io.camunda.zeebe.gateway.rest.controller.CamundaRestController;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestController
@RequestMapping("/v2/users/{userKey}/roles")
public class UserRolesController {

  private final RoleServices roleServices;

  public UserRolesController(final RoleServices roleServices) {
    this.roleServices = roleServices;
  }

  @CamundaPostMapping(path = "/search")
  public ResponseEntity<RoleSearchQueryResult> searchRoles(
      @PathVariable("userKey") final long userKey,
      @RequestBody(required = false) final RoleSearchQueryRequest queryRequest) {
    return SearchQueryRequestMapper.toRoleQuery(queryRequest)
        .fold(RestErrorMapper::mapProblemToResponse, query -> searchRoles(userKey, query));
  }

  private ResponseEntity<RoleSearchQueryResult> searchRoles(
      final long userKey, final RoleQuery query) {
    try {
      final var result =
          roleServices
              .withAuthentication(RequestMapper.getAuthentication())
              .getMemberRoles(String.valueOf(userKey), query);
      return ResponseEntity.ok(SearchQueryResponseMapper.toRoleSearchQueryResponse(result));
    } catch (final Exception e) {
      return RestErrorMapper.mapErrorToResponse(e);
    }
  }
}

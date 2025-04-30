/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.usermanagement;

import io.camunda.service.UserServices;
import io.camunda.zeebe.gateway.protocol.rest.UserSearchQueryRequest;
import io.camunda.zeebe.gateway.protocol.rest.UserSearchResponse;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import io.camunda.zeebe.gateway.rest.controller.CamundaRestQueryController;
import org.apache.commons.lang3.NotImplementedException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestQueryController
@RequestMapping("/v2/users")
public class UserQueryController {

  private final UserServices userServices;

  public UserQueryController(final UserServices userServices) {
    this.userServices = userServices;
  }

  @CamundaPostMapping(path = "/search")
  public ResponseEntity<UserSearchResponse> searchUsers(
      @RequestBody(required = false) final UserSearchQueryRequest query) {
    throw new NotImplementedException("User search is not yet implemented");
  }
}

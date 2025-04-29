/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.usermanagement;

import io.camunda.service.AuthorizationServices;
import io.camunda.zeebe.gateway.protocol.rest.AuthorizationPatchRequest;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import io.camunda.zeebe.gateway.rest.controller.CamundaRestController;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import java.util.concurrent.CompletableFuture;
import org.apache.commons.lang3.NotImplementedException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestController
@RequestMapping("/v2/authorizations")
public class AuthorizationController {
  private final AuthorizationServices<AuthorizationRecord> authorizationServices;

  public AuthorizationController(
      final AuthorizationServices<AuthorizationRecord> authorizationServices) {
    this.authorizationServices = authorizationServices;
  }

  @CamundaPostMapping(path = "/{ownerKey}")
  public CompletableFuture<ResponseEntity<Object>> patchAuthorization(
      @PathVariable final long ownerKey,
      @RequestBody final AuthorizationPatchRequest authorizationPatchRequest) {

    throw new NotImplementedException("Authorization patching is not yet implemented");
  }
}

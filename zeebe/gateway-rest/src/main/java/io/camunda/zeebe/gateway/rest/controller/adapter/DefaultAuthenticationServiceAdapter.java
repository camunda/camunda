/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.adapter;

import static io.camunda.gateway.mapping.http.search.SearchQueryResponseMapper.toCamundaUser;

import io.camunda.authentication.service.CamundaUserService;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.spring.utils.ConditionalOnSecondaryStorageEnabled;
import io.camunda.zeebe.gateway.rest.controller.generated.AuthenticationServiceAdapter;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
@Profile("consolidated-auth")
@ConditionalOnSecondaryStorageEnabled
public class DefaultAuthenticationServiceAdapter implements AuthenticationServiceAdapter {

  private final CamundaUserService camundaUserService;

  public DefaultAuthenticationServiceAdapter(final CamundaUserService camundaUserService) {
    this.camundaUserService = camundaUserService;
  }

  @Override
  public ResponseEntity<Object> getAuthentication(final CamundaAuthentication authentication) {
    final var authenticatedUser = camundaUserService.getCurrentUser();
    return authenticatedUser == null
        ? ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        : ResponseEntity.ok(toCamundaUser(authenticatedUser));
  }
}

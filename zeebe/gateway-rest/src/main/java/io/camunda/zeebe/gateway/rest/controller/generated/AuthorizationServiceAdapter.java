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

import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedAuthorizationRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedAuthorizationSearchQueryRequestStrictContract;
import io.camunda.security.auth.CamundaAuthentication;
import jakarta.annotation.Generated;
import org.springframework.http.ResponseEntity;

/**
 * Service adapter for Authorization operations.
 * Implements request mapping, service delegation, and response construction.
 */
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public interface AuthorizationServiceAdapter {

  ResponseEntity<Object> createAuthorization(
      GeneratedAuthorizationRequestStrictContract authorizationRequest,
      CamundaAuthentication authentication
  );

  ResponseEntity<Void> updateAuthorization(
      Long authorizationKey,
      GeneratedAuthorizationRequestStrictContract authorizationRequest,
      CamundaAuthentication authentication
  );

  ResponseEntity<Object> getAuthorization(
      Long authorizationKey,
      CamundaAuthentication authentication
  );

  ResponseEntity<Void> deleteAuthorization(
      Long authorizationKey,
      CamundaAuthentication authentication
  );

  ResponseEntity<Object> searchAuthorizations(
      GeneratedAuthorizationSearchQueryRequestStrictContract authorizationSearchQuery,
      CamundaAuthentication authentication
  );
}

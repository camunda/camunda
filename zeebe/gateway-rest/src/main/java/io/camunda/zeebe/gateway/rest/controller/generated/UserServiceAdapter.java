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

import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedUserRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedUserSearchQueryRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedUserUpdateRequestStrictContract;
import io.camunda.security.auth.CamundaAuthentication;
import jakarta.annotation.Generated;
import org.springframework.http.ResponseEntity;

/**
 * Service adapter for User operations.
 * Implements request mapping, service delegation, and response construction.
 */
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public interface UserServiceAdapter {

  ResponseEntity<Object> createUser(
      GeneratedUserRequestStrictContract userRequest,
      CamundaAuthentication authentication
  );

  ResponseEntity<Object> searchUsers(
      GeneratedUserSearchQueryRequestStrictContract userSearchQueryRequest,
      CamundaAuthentication authentication
  );

  ResponseEntity<Object> getUser(
      String username,
      CamundaAuthentication authentication
  );

  ResponseEntity<Object> updateUser(
      String username,
      GeneratedUserUpdateRequestStrictContract userUpdateRequest,
      CamundaAuthentication authentication
  );

  ResponseEntity<Void> deleteUser(
      String username,
      CamundaAuthentication authentication
  );
}

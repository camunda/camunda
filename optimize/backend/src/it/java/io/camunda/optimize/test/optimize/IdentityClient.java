/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.test.optimize;

import static io.camunda.optimize.rest.RestTestConstants.DEFAULT_PASSWORD;
import static io.camunda.optimize.rest.RestTestConstants.DEFAULT_USERNAME;

import io.camunda.optimize.OptimizeRequestExecutor;
import io.camunda.optimize.dto.optimize.IdentityWithMetadataResponseDto;
import io.camunda.optimize.dto.optimize.query.IdentitySearchResultResponseDto;
import io.camunda.optimize.dto.optimize.rest.UserResponseDto;
import jakarta.ws.rs.core.Response;
import java.util.function.Supplier;

public class IdentityClient {

  private final Supplier<OptimizeRequestExecutor> requestExecutorSupplier;

  public IdentityClient(final Supplier<OptimizeRequestExecutor> requestExecutorSupplier) {
    this.requestExecutorSupplier = requestExecutorSupplier;
  }

  public IdentityWithMetadataResponseDto getIdentityById(final String id) {
    return getRequestExecutor()
        .buildGetIdentityById(id)
        .execute(IdentityWithMetadataResponseDto.class, Response.Status.OK.getStatusCode());
  }

  public UserResponseDto getCurrentUserIdentity(final String username, final String password) {
    return getRequestExecutor()
        .buildCurrentUserIdentity()
        .withUserAuthentication(username, password)
        .execute(UserResponseDto.class, Response.Status.OK.getStatusCode());
  }

  public IdentitySearchResultResponseDto searchForIdentity(
      final String searchTerms, final Integer limit) {
    return searchForIdentity(searchTerms, limit, DEFAULT_USERNAME, DEFAULT_PASSWORD);
  }

  public IdentitySearchResultResponseDto searchForIdentity(
      final String searchTerms, final String username, final String password) {
    return searchForIdentity(searchTerms, null, username, password);
  }

  public IdentitySearchResultResponseDto searchForIdentity(final String searchTerms) {
    return searchForIdentity(searchTerms, null);
  }

  public IdentitySearchResultResponseDto searchForIdentity(
      final String searchTerms, final boolean excludeUserGroups) {
    return getRequestExecutor()
        .buildSearchForIdentities(searchTerms, null, excludeUserGroups)
        .execute(IdentitySearchResultResponseDto.class, Response.Status.OK.getStatusCode());
  }

  public IdentitySearchResultResponseDto searchForIdentity(
      final String searchTerms, final Integer limit, final String username, final String password) {
    return getRequestExecutor()
        .buildSearchForIdentities(searchTerms, limit)
        .withUserAuthentication(username, password)
        .execute(IdentitySearchResultResponseDto.class, Response.Status.OK.getStatusCode());
  }

  private OptimizeRequestExecutor getRequestExecutor() {
    return requestExecutorSupplier.get();
  }
}

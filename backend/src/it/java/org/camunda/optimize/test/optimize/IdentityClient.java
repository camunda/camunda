/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.test.optimize;

import lombok.AllArgsConstructor;
import org.camunda.optimize.OptimizeRequestExecutor;
import org.camunda.optimize.dto.optimize.IdentityWithMetadataResponseDto;
import org.camunda.optimize.dto.optimize.query.IdentitySearchResultResponseDto;
import org.camunda.optimize.dto.optimize.rest.UserResponseDto;

import javax.ws.rs.core.Response;
import java.util.function.Supplier;

import static org.camunda.optimize.rest.RestTestConstants.DEFAULT_PASSWORD;
import static org.camunda.optimize.rest.RestTestConstants.DEFAULT_USERNAME;

@AllArgsConstructor
public class IdentityClient {
  private final Supplier<OptimizeRequestExecutor> requestExecutorSupplier;


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

  public IdentitySearchResultResponseDto searchForIdentity(final String searchTerms, final Integer limit) {
    return searchForIdentity(searchTerms, limit, DEFAULT_USERNAME, DEFAULT_PASSWORD);
  }

  public IdentitySearchResultResponseDto searchForIdentity(final String searchTerms, final String username,
                                                           final String password) {
    return searchForIdentity(searchTerms, null, username, password);
  }

  public IdentitySearchResultResponseDto searchForIdentity(final String searchTerms) {
    return searchForIdentity(searchTerms, null);
  }

  public IdentitySearchResultResponseDto searchForIdentity(final String searchTerms, final boolean excludeUserGroups) {
    return getRequestExecutor()
      .buildSearchForIdentities(searchTerms, null, excludeUserGroups)
      .execute(IdentitySearchResultResponseDto.class, Response.Status.OK.getStatusCode());
  }

  public IdentitySearchResultResponseDto searchForIdentity(final String searchTerms, final Integer limit,
                                                           final String username, final String password) {
    return getRequestExecutor()
      .buildSearchForIdentities(searchTerms, limit)
      .withUserAuthentication(username, password)
      .execute(IdentitySearchResultResponseDto.class, Response.Status.OK.getStatusCode());
  }

  private OptimizeRequestExecutor getRequestExecutor() {
    return requestExecutorSupplier.get();
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.test.optimize;

import lombok.AllArgsConstructor;
import org.camunda.optimize.OptimizeRequestExecutor;
import org.camunda.optimize.dto.optimize.IdentityWithMetadataDto;
import org.camunda.optimize.dto.optimize.UserDto;
import org.camunda.optimize.dto.optimize.query.IdentitySearchResultDto;

import javax.ws.rs.core.Response;
import java.util.function.Supplier;

import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_PASSWORD;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;

@AllArgsConstructor
public class IdentityClient {
  private final Supplier<OptimizeRequestExecutor> requestExecutorSupplier;


  public IdentityWithMetadataDto getIdentityById(final String id) {
    return getRequestExecutor()
      .buildGetIdentityById(id)
      .execute(IdentityWithMetadataDto.class, Response.Status.OK.getStatusCode());
  }

  public UserDto getCurrentUserIdentity(final String username, final String password) {
    return getRequestExecutor()
      .buildCurrentUserIdentity()
      .withUserAuthentication(username, password)
      .execute(UserDto.class, Response.Status.OK.getStatusCode());
  }

  public IdentitySearchResultDto searchForIdentity(final String searchTerms, final Integer limit) {
    return searchForIdentity(searchTerms, limit, DEFAULT_USERNAME, DEFAULT_PASSWORD);
  }

  public IdentitySearchResultDto searchForIdentity(final String searchTerms, final String username, final String password) {
    return searchForIdentity(searchTerms, null, username, password);
  }

  public IdentitySearchResultDto searchForIdentity(final String searchTerms) {
    return searchForIdentity(searchTerms, null);
  }

  public IdentitySearchResultDto searchForIdentity(final String searchTerms, final Integer limit,
                                                   final String username, final String password) {
    return getRequestExecutor()
      .buildSearchForIdentities(searchTerms, limit)
      .withUserAuthentication(username, password)
      .execute(IdentitySearchResultDto.class, Response.Status.OK.getStatusCode());
  }

  private OptimizeRequestExecutor getRequestExecutor() {
    return requestExecutorSupplier.get();
  }
}

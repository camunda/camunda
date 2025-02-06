/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.auth;

import io.camunda.search.clients.core.SearchQueryRequest;
import io.camunda.search.query.SearchQueryBase;
import io.camunda.security.auth.SecurityContext;

/** Strategy to apply authorization to a search query. */
public interface AuthorizationQueryStrategy {

  AuthorizationQueryStrategy NONE = (request, securityContext, queryClass) -> request;

  /**
   * Apply authorization to a search query.
   *
   * @param searchQueryRequest
   * @param securityContext
   * @param queryClass
   * @return the search query request with authorization applied
   */
  SearchQueryRequest applyAuthorizationToQuery(
      SearchQueryRequest searchQueryRequest,
      SecurityContext securityContext,
      Class<? extends SearchQueryBase> queryClass);
}

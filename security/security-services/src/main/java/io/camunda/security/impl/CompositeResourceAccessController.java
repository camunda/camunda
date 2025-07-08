/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.impl;

import static io.camunda.search.exception.ErrorMessages.ERROR_RESOURCE_ACCESS_CONTROLLER_NOT_FOUND;

import io.camunda.search.clients.security.ResourceAccessChecks;
import io.camunda.search.clients.security.ResourceAccessController;
import io.camunda.search.exception.CamundaSearchException;
import io.camunda.search.exception.ResourceAccessForbiddenException;
import io.camunda.search.exception.TenantAccessForbiddenException;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.SecurityContext;
import java.util.List;
import java.util.function.Function;

public class CompositeResourceAccessController implements ResourceAccessController {

  private final List<ResourceAccessController> controllers;

  public CompositeResourceAccessController(final List<ResourceAccessController> controllers) {
    this.controllers = controllers;
  }

  @Override
  public <T> T doGet(
      final SecurityContext securityContext,
      final Function<ResourceAccessChecks, T> accessChecksApplier)
      throws ResourceAccessForbiddenException, TenantAccessForbiddenException {
    return getMatchingController(securityContext).doGet(securityContext, accessChecksApplier);
  }

  @Override
  public <T> SearchQueryResult<T> doSearch(
      final SecurityContext securityContext,
      final Function<ResourceAccessChecks, SearchQueryResult<T>> accessChecksApplier)
      throws ResourceAccessForbiddenException, TenantAccessForbiddenException {
    return getMatchingController(securityContext).doSearch(securityContext, accessChecksApplier);
  }

  @Override
  public <T> List<T> doAggregate(
      final SecurityContext securityContext,
      final Function<ResourceAccessChecks, List<T>> accessChecksApplier)
      throws ResourceAccessForbiddenException, TenantAccessForbiddenException {
    return getMatchingController(securityContext).doAggregate(securityContext, accessChecksApplier);
  }

  protected ResourceAccessController getMatchingController(final SecurityContext securityContext) {
    return controllers.stream()
        .filter(controller -> controller.supports(securityContext))
        .findFirst()
        .orElseThrow(() -> new CamundaSearchException(ERROR_RESOURCE_ACCESS_CONTROLLER_NOT_FOUND));
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.auth;

import static io.camunda.search.exception.ErrorMessages.ERROR_RESOURCE_ACCESS_CONTROLLER_NO_MATCHING_FOUND;

import io.camunda.search.exception.CamundaSearchException;
import io.camunda.security.auth.SecurityContext;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.security.reader.ResourceAccessController;
import java.util.List;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ResourceAccessDelegatingController implements ResourceAccessController {

  private static final Logger LOG =
      LoggerFactory.getLogger(ResourceAccessDelegatingController.class);

  private final List<ResourceAccessController> controllers;

  public ResourceAccessDelegatingController(final List<ResourceAccessController> controllers) {
    this.controllers = controllers;
  }

  @Override
  public <T> T doGet(
      final SecurityContext securityContext,
      final Function<ResourceAccessChecks, T> resourceChecksApplier) {
    return getMatchingController(securityContext).doGet(securityContext, resourceChecksApplier);
  }

  @Override
  public <T> T doSearch(
      final SecurityContext securityContext,
      final Function<ResourceAccessChecks, T> resourceChecksApplier) {
    return getMatchingController(securityContext).doSearch(securityContext, resourceChecksApplier);
  }

  @Override
  public boolean supports(final SecurityContext securityContext) {
    return true;
  }

  protected ResourceAccessController getMatchingController(final SecurityContext securityContext) {
    return controllers.stream()
        .filter(controller -> controller.supports(securityContext))
        .findFirst()
        .orElseThrow(
            () -> {
              LOG.error(ERROR_RESOURCE_ACCESS_CONTROLLER_NO_MATCHING_FOUND);
              return new CamundaSearchException(ERROR_RESOURCE_ACCESS_CONTROLLER_NO_MATCHING_FOUND);
            });
  }
}

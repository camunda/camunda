/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.security;

import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

public class ResolvedResourceTypeAuthorizations {

  @Getter
  @Setter
  private String engine;
  private boolean canSeeAll = false;
  private final Set<String> authorizedResources = new HashSet<>();
  private final Set<String> prohibitedResources = new HashSet<>();

  public void grantToSeeAllResources() {
    canSeeAll = true;
    prohibitedResources.clear();
    authorizedResources.clear();
  }

  public void revokeToSeeAllResources() {
    canSeeAll = false;
    authorizedResources.clear();
    prohibitedResources.clear();
  }

  public void authorizeResource(final String resourceId) {
    authorizedResources.add(resourceId);
    prohibitedResources.remove(resourceId);
  }

  public void prohibitResource(final String resourceId) {
    prohibitedResources.add(resourceId);
    authorizedResources.remove(resourceId);
  }

  public boolean isAuthorizedToAccessResource(final String resourceId) {
    if (canSeeAll) {
      return !prohibitedResources.contains(resourceId);
    } else {
      return authorizedResources.contains(resourceId);
    }
  }
}

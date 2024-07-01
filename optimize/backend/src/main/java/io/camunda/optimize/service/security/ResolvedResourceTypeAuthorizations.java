/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.security;

import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

public class ResolvedResourceTypeAuthorizations {

  @Getter @Setter private String engine;
  private boolean canSeeAll = false;
  private final Set<String> authorizedResources = new HashSet<>();
  private final Set<String> prohibitedResources = new HashSet<>();

  public void merge(final ResolvedResourceTypeAuthorizations authorizationsToMerge) {
    this.canSeeAll = this.canSeeAll || authorizationsToMerge.canSeeAll;
    this.authorizedResources.addAll(authorizationsToMerge.authorizedResources);
    this.prohibitedResources.addAll(authorizationsToMerge.prohibitedResources);
  }

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

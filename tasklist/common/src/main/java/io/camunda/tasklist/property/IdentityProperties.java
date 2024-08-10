/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.property;

public class IdentityProperties {
  public static final String ALL_RESOURCES = "*";
  public static final String FULL_GROUP_ACCESS = "";
  private String redirectRootUrl;
  private boolean resourcePermissionsEnabled = false;
  private boolean userAccessRestrictionsEnabled = true;

  public String getRedirectRootUrl() {
    return redirectRootUrl;
  }

  public void setRedirectRootUrl(final String redirectRootUrl) {
    this.redirectRootUrl = redirectRootUrl;
  }

  public boolean isUserAccessRestrictionsEnabled() {
    return userAccessRestrictionsEnabled;
  }

  public IdentityProperties setUserAccessRestrictionsEnabled(
      final boolean userAccessRestrictionsEnabled) {
    this.userAccessRestrictionsEnabled = userAccessRestrictionsEnabled;
    return this;
  }

  public boolean isResourcePermissionsEnabled() {
    return resourcePermissionsEnabled;
  }

  public IdentityProperties setResourcePermissionsEnabled(
      final boolean resourcePermissionsEnabled) {
    this.resourcePermissionsEnabled = resourcePermissionsEnabled;
    return this;
  }
}

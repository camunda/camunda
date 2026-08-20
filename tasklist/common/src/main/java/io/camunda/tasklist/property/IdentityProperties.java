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
  private boolean userAccessRestrictionsEnabled = true;

  public boolean isUserAccessRestrictionsEnabled() {
    return userAccessRestrictionsEnabled;
  }

  public IdentityProperties setUserAccessRestrictionsEnabled(
      final boolean userAccessRestrictionsEnabled) {
    this.userAccessRestrictionsEnabled = userAccessRestrictionsEnabled;
    return this;
  }
}

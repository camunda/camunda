/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration.users;

import java.util.Objects;

public class UsersConfiguration {

  private CloudUsersConfiguration cloud;

  public UsersConfiguration() {}

  public CloudUsersConfiguration getCloud() {
    return cloud;
  }

  public void setCloud(final CloudUsersConfiguration cloud) {
    this.cloud = cloud;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof UsersConfiguration;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final UsersConfiguration that = (UsersConfiguration) o;
    return Objects.equals(cloud, that.cloud);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(cloud);
  }

  @Override
  public String toString() {
    return "UsersConfiguration(cloud=" + getCloud() + ")";
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration.users;

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
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $cloud = getCloud();
    result = result * PRIME + ($cloud == null ? 43 : $cloud.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof UsersConfiguration)) {
      return false;
    }
    final UsersConfiguration other = (UsersConfiguration) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$cloud = getCloud();
    final Object other$cloud = other.getCloud();
    if (this$cloud == null ? other$cloud != null : !this$cloud.equals(other$cloud)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "UsersConfiguration(cloud=" + getCloud() + ")";
  }
}

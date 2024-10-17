/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration.engine;

import static io.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.IDENTITY_SYNC_CONFIGURATION;

public class UserIdentityCacheConfiguration extends IdentityCacheConfiguration {

  public UserIdentityCacheConfiguration() {}

  @Override
  public String toString() {
    return "UserIdentityCacheConfiguration()";
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof UserIdentityCacheConfiguration)) {
      return false;
    }
    final UserIdentityCacheConfiguration other = (UserIdentityCacheConfiguration) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    return true;
  }

  @Override
  protected boolean canEqual(final Object other) {
    return other instanceof UserIdentityCacheConfiguration;
  }

  @Override
  public int hashCode() {
    final int result = super.hashCode();
    return result;
  }

  @Override
  public String getConfigName() {
    return IDENTITY_SYNC_CONFIGURATION;
  }
}

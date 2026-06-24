/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration.engine;

import static io.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.IDENTITY_SYNC_CONFIGURATION;

import java.util.Objects;

public class UserIdentityCacheConfiguration extends IdentityCacheConfiguration {

  public UserIdentityCacheConfiguration() {}

  @Override
  public String toString() {
    return "UserIdentityCacheConfiguration()";
  }

  @Override
  protected boolean canEqual(final Object other) {
    return other instanceof UserIdentityCacheConfiguration;
  }

  @Override
  public int hashCode() {
    return Objects.hash(getClass(), super.hashCode());
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    return super.equals(o);
  }

  @Override
  public String getConfigName() {
    return IDENTITY_SYNC_CONFIGURATION;
  }
}

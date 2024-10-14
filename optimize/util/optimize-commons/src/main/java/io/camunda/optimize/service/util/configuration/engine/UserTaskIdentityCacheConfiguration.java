/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration.engine;

import static io.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.IMPORT_USER_TASK_IDENTITY_META_DATA;

public class UserTaskIdentityCacheConfiguration extends IdentityCacheConfiguration {

  public UserTaskIdentityCacheConfiguration() {}

  @Override
  public String getConfigName() {
    return IMPORT_USER_TASK_IDENTITY_META_DATA;
  }

  @Override
  protected boolean canEqual(final Object other) {
    return other instanceof UserTaskIdentityCacheConfiguration;
  }

  @Override
  public int hashCode() {
    final int result = super.hashCode();
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof UserTaskIdentityCacheConfiguration)) {
      return false;
    }
    final UserTaskIdentityCacheConfiguration other = (UserTaskIdentityCacheConfiguration) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "UserTaskIdentityCacheConfiguration()";
  }
}

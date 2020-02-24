/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.legacy.tomlconfig.gateway;

import static io.zeebe.legacy.tomlconfig.gateway.ConfigurationDefaults.DEFAULT_MANAGEMENT_THREADS;
import static io.zeebe.legacy.tomlconfig.gateway.EnvironmentConstants.ENV_GATEWAY_MANAGEMENT_THREADS;

import io.zeebe.util.Environment;
import java.util.Objects;

@Deprecated(since = "0.23.0-alpha2", forRemoval = true)
/* Kept in order to be able to offer a migration path for old configurations.
 */
public final class ThreadsCfg {

  private int managementThreads = DEFAULT_MANAGEMENT_THREADS;

  public void init(final Environment environment) {
    environment.getInt(ENV_GATEWAY_MANAGEMENT_THREADS).ifPresent(this::setManagementThreads);
  }

  public int getManagementThreads() {
    return managementThreads;
  }

  public ThreadsCfg setManagementThreads(final int managementThreads) {
    this.managementThreads = managementThreads;
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(managementThreads);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ThreadsCfg that = (ThreadsCfg) o;
    return managementThreads == that.managementThreads;
  }

  @Override
  public String toString() {
    return "ThreadsCfg{" + "managementThreads=" + managementThreads + '}';
  }
}

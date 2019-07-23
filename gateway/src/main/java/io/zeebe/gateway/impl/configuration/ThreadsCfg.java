/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.gateway.impl.configuration;

import static io.zeebe.gateway.impl.configuration.ConfigurationDefaults.DEFAULT_MANAGEMENT_THREADS;
import static io.zeebe.gateway.impl.configuration.EnvironmentConstants.ENV_GATEWAY_MANAGEMENT_THREADS;

import io.zeebe.util.Environment;
import java.util.Objects;

public class ThreadsCfg {

  private int managementThreads = DEFAULT_MANAGEMENT_THREADS;

  public void init(Environment environment) {
    environment.getInt(ENV_GATEWAY_MANAGEMENT_THREADS).ifPresent(this::setManagementThreads);
  }

  public int getManagementThreads() {
    return managementThreads;
  }

  public ThreadsCfg setManagementThreads(int managementThreads) {
    this.managementThreads = managementThreads;
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(managementThreads);
  }

  @Override
  public boolean equals(Object o) {
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

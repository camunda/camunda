/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.gateway.impl.configuration;

import static io.zeebe.gateway.impl.configuration.ConfigurationDefaults.DEFAULT_MANAGEMENT_THREADS;

import java.util.Objects;

public final class ThreadsCfg {

  private int managementThreads = DEFAULT_MANAGEMENT_THREADS;

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

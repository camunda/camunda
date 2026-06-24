/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.configuration;

import static io.camunda.zeebe.gateway.impl.configuration.ConfigurationDefaults.DEFAULT_MANAGEMENT_THREADS;

import java.util.Objects;

public final class ThreadsCfg {

  private int managementThreads = DEFAULT_MANAGEMENT_THREADS;
  private int grpcMinThreads = Runtime.getRuntime().availableProcessors();
  private int grpcMaxThreads = 2 * Runtime.getRuntime().availableProcessors();

  public int getManagementThreads() {
    return managementThreads;
  }

  public ThreadsCfg setManagementThreads(final int managementThreads) {
    this.managementThreads = managementThreads;
    return this;
  }

  public int getGrpcMinThreads() {
    return grpcMinThreads;
  }

  public void setGrpcMinThreads(final int grpcMinThreads) {
    this.grpcMinThreads = grpcMinThreads;
  }

  public int getGrpcMaxThreads() {
    return grpcMaxThreads;
  }

  public void setGrpcMaxThreads(final int grpcMaxThreads) {
    this.grpcMaxThreads = grpcMaxThreads;
  }

  @Override
  public int hashCode() {
    return Objects.hash(managementThreads, grpcMinThreads, grpcMaxThreads);
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
    return managementThreads == that.managementThreads
        && grpcMinThreads == that.grpcMinThreads
        && grpcMaxThreads == that.grpcMaxThreads;
  }

  @Override
  public String toString() {
    return "ThreadsCfg{"
        + "managementThreads="
        + managementThreads
        + ", grpcMinThreads="
        + grpcMinThreads
        + ", grpcMaxThreads="
        + grpcMaxThreads
        + '}';
  }
}

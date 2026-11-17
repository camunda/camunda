/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.api;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Where the cluster-wide REST API ({@code /cluster/v2/**}) is served. Read by {@code
 * io.camunda.authentication.clusteradmin.ClusterApiSettings}; this class documents the properties.
 */
@NullMarked
public class ClusterApi {

  /**
   * Dedicated port for the cluster API. When unset, the cluster API shares {@code server.port} with
   * the rest of the REST API. When set, the cluster API is served only on this port (except its
   * public status endpoints), and the main port no longer serves it. The port inherits the main
   * port's TLS and connector settings.
   */
  private @Nullable Integer port;

  /**
   * Bind address of the dedicated cluster API port. Defaults to {@code server.address}. Required
   * when cluster-admin authentication is {@code none}. Startup fails if it cannot be resolved.
   */
  private @Nullable String address;

  public @Nullable Integer getPort() {
    return port;
  }

  public void setPort(final @Nullable Integer port) {
    this.port = port;
  }

  public @Nullable String getAddress() {
    return address;
  }

  public void setAddress(final @Nullable String address) {
    this.address = address;
  }
}

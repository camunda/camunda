/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.security.clusteradmin;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public class ClusterAdminAuthentication {

  /**
   * Authentication of the cluster-admin API: {@code basic}, {@code oidc} or {@code none}. When
   * unset, follows {@code camunda.security.authentication.method}. {@code oidc} requires the
   * deployment to use OIDC; {@code none} requires a dedicated cluster API port ({@code
   * camunda.api.cluster.port}).
   */
  private @Nullable String method;

  public @Nullable String getMethod() {
    return method;
  }

  public void setMethod(final @Nullable String method) {
    this.method = method;
  }
}

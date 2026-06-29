/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.appint;

/**
 * Immutable carrier for the deployment-level identifiers captured from the exporter {@link
 * io.camunda.zeebe.exporter.api.context.Context} (and the environment) at configuration time.
 *
 * <p>These are forwarded to the App Integration Backend as context headers. Any field may be {@code
 * null} when the corresponding value is not available; consumers decide which headers to emit.
 *
 * @param orgId the organization id (SaaS), read from the {@value #ORGANIZATION_ID_ENV_VAR}
 *     environment variable; {@code null} when not set.
 * @param clusterId the cluster id (SaaS), read from the {@value #CLUSTER_ID_ENV_VAR} environment
 *     variable; {@code null}/empty when not set.
 */
public record DeploymentContext(String orgId, String clusterId) {

  public static final String ORGANIZATION_ID_ENV_VAR = "CAMUNDA_CLOUD_ORGANIZATION_ID";

  /**
   * Environment variable carrying the broker cluster id. Mirrors the fallback used by the exporter
   * {@code Context#getClusterId()} on newer brokers, so SaaS deployments emit the cluster id even
   * though the 8.9 exporter {@code Context} does not expose it natively.
   */
  public static final String CLUSTER_ID_ENV_VAR = "ZEEBE_BROKER_CLUSTER_CLUSTERID";

  public static final DeploymentContext EMPTY = new DeploymentContext(null, null);
}

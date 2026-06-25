/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.appint.transport;

import java.util.function.BiConsumer;

/**
 * Resolved context-identification headers attached to every export request so the backend can tell
 * which cluster/org a batch originates from.
 *
 * <p>Each value is sourced independently and a header is only emitted when its value is available;
 * there is no dependency on the deployment model (SaaS vs Self-Managed) or the authentication
 * mechanism.
 */
public record ContextHeaders(String orgId, String clusterId) {

  public static final String X_ORG_ID = "X-Org-Id";
  public static final String X_CLUSTER_ID = "X-Cluster-Id";
  public static final ContextHeaders EMPTY = new ContextHeaders(null, null);

  /** Sentinel some SaaS environments use to indicate the organization id is unset. */
  private static final String ORG_ID_UNSET_SENTINEL = "null";

  /**
   * Resolves the headers from their possible sources.
   *
   * @param configClusterId the operator-supplied cluster id (exporter config); takes precedence.
   * @param envClusterId the cluster id read from the environment (SaaS).
   * @param orgId the organization id read from the environment.
   */
  public static ContextHeaders resolve(
      final String configClusterId, final String envClusterId, final String orgId) {
    return new ContextHeaders(normalizeOrgId(orgId), firstNonBlank(configClusterId, envClusterId));
  }

  /** Emits each resolved header via the given consumer, skipping any blank value. */
  public void applyTo(final BiConsumer<String, String> headerConsumer) {
    if (isPresent(orgId)) {
      headerConsumer.accept(X_ORG_ID, orgId);
    }
    if (isPresent(clusterId)) {
      headerConsumer.accept(X_CLUSTER_ID, clusterId);
    }
  }

  private static String normalizeOrgId(final String orgId) {
    if (!isPresent(orgId) || ORG_ID_UNSET_SENTINEL.equals(orgId)) {
      return null;
    }
    return orgId;
  }

  private static String firstNonBlank(final String preferred, final String fallback) {
    if (isPresent(preferred)) {
      return preferred;
    }
    return isPresent(fallback) ? fallback : null;
  }

  private static boolean isPresent(final String value) {
    return value != null && !value.isBlank();
  }
}

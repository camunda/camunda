/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.admin.exporting;

import java.util.concurrent.CompletableFuture;

/**
 * Every operation targets a single physical tenant (partition group). The {@code physicalTenantId}
 * selects the partition set the operation enumerates and is stamped on every outgoing broker
 * request so that routing resolves leaders from that group's topology. Callers that do not care
 * about physical tenants pass {@link
 * io.camunda.cluster.PhysicalTenantIds#DEFAULT_PHYSICAL_TENANT_ID}.
 */
public interface ExportingControlApi {
  CompletableFuture<Void> pauseExporting(String physicalTenantId);

  CompletableFuture<Void> softPauseExporting(String physicalTenantId);

  CompletableFuture<Void> resumeExporting(String physicalTenantId);
}

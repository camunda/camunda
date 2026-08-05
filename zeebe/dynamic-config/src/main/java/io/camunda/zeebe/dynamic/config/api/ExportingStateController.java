/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.api;

import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.NullMarked;

/**
 * Controls exporting for the partitions of a single physical tenant. The returned futures complete
 * once the requested state has actually been applied, so callers can expose a synchronous
 * request/response contract.
 *
 * <p>The physical tenant id is part of the contract even though the only implementation cannot yet
 * honour it — dynamic cluster configuration has no per-physical-tenant exporting state. Keeping the
 * parameter here means callers already pass the right scope and the scoping can be implemented in
 * one place once dynamic configuration supports it.
 */
@NullMarked
public interface ExportingStateController {

  CompletableFuture<Void> pauseExporting(String physicalTenantId);

  CompletableFuture<Void> softPauseExporting(String physicalTenantId);

  CompletableFuture<Void> resumeExporting(String physicalTenantId);
}

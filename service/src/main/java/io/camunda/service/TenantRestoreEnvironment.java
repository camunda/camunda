/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import org.jspecify.annotations.NullMarked;

/**
 * The restore inputs a physical tenant contributes from its own secondary-storage configuration
 * rather than from the request body: which backup store it runs, so the validator can apply the
 * right rules, and whether it takes continuous backups, so a time-range restore is accepted.
 *
 * <p>Backup store choice is a per-tenant deployment decision, not a per-request one, so this is
 * resolved once per physical tenant at startup — see {@code CamundaServicesConfiguration}, which
 * binds it from that tenant's {@code Camunda} configuration — and handed to {@link
 * RecoveryServices} and {@link ClusterRecoveryServices} rather than read from the process-wide
 * {@code Environment} on every request.
 */
@NullMarked
public record TenantRestoreEnvironment(String databaseType, boolean continuousBackups) {}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.shared.management;

import io.camunda.zeebe.shared.profiles.ProfileStandaloneBrokerOrGateway;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.actuate.endpoint.annotation.DeleteOperation;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;
import org.springframework.boot.actuate.endpoint.annotation.Selector.Match;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.boot.actuate.endpoint.web.WebEndpointResponse;
import org.springframework.boot.actuate.endpoint.web.annotation.WebEndpoint;
import org.springframework.stereotype.Component;

/**
 * The deprecated {@code backups} alias of {@link BackupEndpoint}, exposed on standalone brokers and
 * gateways. It mirrors that endpoint's operation signatures verbatim, including the parameterless
 * {@code POST} overloads, so both ids answer the same requests the same way.
 */
@Component
@WebEndpoint(id = "backups")
@ProfileStandaloneBrokerOrGateway
public final class BackupEndpointStandalone {
  private final BackupEndpoint backupEndpoint;

  BackupEndpointStandalone(final BackupEndpoint backupEndpoint) {
    this.backupEndpoint = backupEndpoint;
  }

  @WriteOperation
  public WebEndpointResponse<?> take(
      final @Nullable Long backupId, final @Nullable String physicalTenant) {
    return backupEndpoint.take(backupId, physicalTenant);
  }

  @WriteOperation
  public WebEndpointResponse<?> take() {
    return backupEndpoint.take();
  }

  @WriteOperation
  public WebEndpointResponse<?> write(
      @Selector(match = Match.ALL_REMAINING) final String[] path,
      final @Nullable String physicalTenant) {
    return backupEndpoint.write(path, physicalTenant);
  }

  @WriteOperation
  public WebEndpointResponse<?> write(@Selector(match = Match.ALL_REMAINING) final String[] path) {
    return backupEndpoint.write(path);
  }

  @ReadOperation
  public WebEndpointResponse<?> listAll(
      final @Nullable String physicalTenant,
      final @Nullable Long before,
      final @Nullable Integer limit) {
    return backupEndpoint.listAll(physicalTenant, before, limit);
  }

  @ReadOperation
  public WebEndpointResponse<?> query(
      @Selector final String prefixOrId,
      final @Nullable String physicalTenant,
      final @Nullable Long before,
      final @Nullable Integer limit) {
    return backupEndpoint.query(prefixOrId, physicalTenant, before, limit);
  }

  @DeleteOperation
  public WebEndpointResponse<?> delete(
      @Selector final String id, final @Nullable String physicalTenant) {
    return backupEndpoint.delete(id, physicalTenant);
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.shared.management;

import io.camunda.zeebe.shared.profiles.ProfileStandaloneBrokerOrGateway;
import org.springframework.boot.actuate.endpoint.annotation.DeleteOperation;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.boot.actuate.endpoint.web.WebEndpointResponse;
import org.springframework.boot.actuate.endpoint.web.annotation.WebEndpoint;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

@Component
@WebEndpoint(id = "backups")
@ProfileStandaloneBrokerOrGateway
public final class BackupEndpointStandalone {
  private final BackupEndpoint backupEndpoint;

  private BackupEndpointStandalone(final BackupEndpoint backupEndpoint) {
    this.backupEndpoint = backupEndpoint;
  }

  @WriteOperation
  public WebEndpointResponse<?> take(final long backupId) {
    return backupEndpoint.take(backupId);
  }

  @ReadOperation
  public WebEndpointResponse<?> listAll() {
    return backupEndpoint.listAll();
  }

  @ReadOperation
  public WebEndpointResponse<?> query(@Selector final String prefixOrId) {
    return backupEndpoint.query(prefixOrId);
  }

  @DeleteOperation
  public WebEndpointResponse<?> delete(@Selector @NonNull final long id) {
    return backupEndpoint.delete(id);
  }
}

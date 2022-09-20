/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.shared.management;

import io.camunda.zeebe.gateway.admin.backup.BackupApi;
import io.camunda.zeebe.gateway.admin.backup.BackupRequestHandler;
import io.camunda.zeebe.gateway.admin.backup.BackupStatus;
import io.camunda.zeebe.gateway.impl.broker.BrokerClient;
import io.camunda.zeebe.util.VisibleForTesting;
import java.util.concurrent.CompletionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.boot.actuate.endpoint.web.WebEndpointResponse;
import org.springframework.boot.actuate.endpoint.web.annotation.WebEndpoint;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

@Component
@WebEndpoint(id = "backups", enableByDefault = false)
final class BackupEndpoint {
  private final BackupApi api;

  @SuppressWarnings("unused") // used by Spring
  @Autowired
  public BackupEndpoint(final BrokerClient client) {
    this(new BackupRequestHandler(client));
  }

  BackupEndpoint(final BackupApi api) {
    this.api = api;
  }

  @WriteOperation
  public WebEndpointResponse<?> take(@Selector @NonNull final long id) {
    try {
      final long backupId = api.takeBackup(id).toCompletableFuture().join();
      return new WebEndpointResponse<>(new TakeBackupResponse(backupId));
    } catch (final CompletionException e) {
      return new WebEndpointResponse<>(
          new ErrorResponse(id, e.getCause().getMessage()),
          WebEndpointResponse.STATUS_INTERNAL_SERVER_ERROR);
    } catch (final Exception e) {
      return new WebEndpointResponse<>(
          new ErrorResponse(id, e.getMessage()), WebEndpointResponse.STATUS_INTERNAL_SERVER_ERROR);
    }
  }

  // TODO: do not use the internal data type directly, but later use the OpenAPI generated models on
  //       both the client and server side
  @ReadOperation
  public WebEndpointResponse<?> status(@Selector @NonNull final long id) {
    try {
      final BackupStatus status = api.getStatus(id).toCompletableFuture().join();
      return new WebEndpointResponse<>(status);
    } catch (final CompletionException e) {
      return new WebEndpointResponse<>(
          new ErrorResponse(id, e.getCause().getMessage()),
          WebEndpointResponse.STATUS_INTERNAL_SERVER_ERROR);
    } catch (final Exception e) {
      return new WebEndpointResponse<>(
          new ErrorResponse(id, e.getMessage()), WebEndpointResponse.STATUS_INTERNAL_SERVER_ERROR);
    }
  }

  @VisibleForTesting
  record ErrorResponse(long id, String failure) {}

  @VisibleForTesting
  record TakeBackupResponse(long id) {}
}

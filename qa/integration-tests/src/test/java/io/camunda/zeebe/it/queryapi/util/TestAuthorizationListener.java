/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.queryapi.util;

import io.camunda.zeebe.gateway.Loggers;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.CancelProcessInstanceRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.CompleteJobRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.CreateProcessInstanceRequest;
import io.camunda.zeebe.gateway.query.QueryApi;
import io.camunda.zeebe.test.util.grpc.CloseAwareListener;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCall.Listener;
import io.grpc.Status;
import java.time.Duration;

public final class TestAuthorizationListener<ReqT> extends CloseAwareListener<ReqT> {
  private final QueryApi api;
  private final ServerCall<ReqT, ?> call;
  private final String tenant;

  public TestAuthorizationListener(
      final Listener<ReqT> delegate,
      final QueryApi api,
      final ServerCall<ReqT, ?> call,
      final String tenant) {
    super(delegate);
    this.api = api;
    this.call = call;
    this.tenant = tenant;
  }

  @Override
  public void onMessage(final ReqT message) {
    Authorization authorization = Authorization.ALLOW;

    if (message instanceof CreateProcessInstanceRequest) {
      authorization = authorizeCreateProcessInstance((CreateProcessInstanceRequest) message);
    } else if (message instanceof CancelProcessInstanceRequest) {
      authorization = authorizeCancelProcessInstance((CancelProcessInstanceRequest) message);
    } else if (message instanceof CompleteJobRequest) {
      authorization = authorizeCompleteJob((CompleteJobRequest) message);
    }

    if (authorization == Authorization.ALLOW) {
      super.onMessage(message);
    }
  }

  private Authorization authorizeCompleteJob(final CompleteJobRequest request) {
    final var asyncProcessId =
        api.getBpmnProcessIdFromJob(request.getJobKey(), Duration.ofSeconds(5));

    final String processId;
    try {
      processId = asyncProcessId.toCompletableFuture().join();
    } catch (final Exception e) {
      onQueryApiError(e);
      return Authorization.DENY;
    }

    if (!processId.startsWith(tenant)) {
      call.close(
          Status.PERMISSION_DENIED.augmentDescription(
              "Failed to complete job as you are not authorized on resource " + processId),
          new Metadata());
      isClosed = true;
      return Authorization.DENY;
    }

    return Authorization.ALLOW;
  }

  private Authorization authorizeCancelProcessInstance(final CancelProcessInstanceRequest request) {
    final var asyncProcessId =
        api.getBpmnProcessIdFromProcessInstance(
            request.getProcessInstanceKey(), Duration.ofSeconds(5));

    final String processId;
    try {
      processId = asyncProcessId.toCompletableFuture().join();
    } catch (final Exception e) {
      onQueryApiError(e);
      return Authorization.DENY;
    }

    if (!processId.startsWith(tenant)) {
      call.close(
          Status.PERMISSION_DENIED.augmentDescription(
              "Failed to cancel process instance as you are not authorized on resource "
                  + processId),
          new Metadata());
      isClosed = true;
      return Authorization.DENY;
    }

    return Authorization.ALLOW;
  }

  private Authorization authorizeCreateProcessInstance(final CreateProcessInstanceRequest request) {
    final String processId;

    if (request.getBpmnProcessId().isBlank()) {
      final var asyncProcessId =
          api.getBpmnProcessIdFromProcess(request.getProcessDefinitionKey(), Duration.ofSeconds(5));
      try {
        processId = asyncProcessId.toCompletableFuture().join();
      } catch (final Exception e) {
        onQueryApiError(e);
        return Authorization.DENY;
      }
    } else {
      processId = request.getBpmnProcessId();
    }

    if (!processId.startsWith(tenant)) {
      call.close(
          Status.PERMISSION_DENIED.augmentDescription(
              "Failed to create process instance as you are not authorized on resource "
                  + processId),
          new Metadata());
      isClosed = true;
      return Authorization.DENY;
    }

    return Authorization.ALLOW;
  }

  private void onQueryApiError(final Exception e) {
    Loggers.GATEWAY_LOGGER.warn("Unexpected error while querying the API for a resource", e);
    call.close(
        Status.ABORTED
            .augmentDescription("Failed to authorize client due to internal error")
            .withCause(e),
        new Metadata());
    isClosed = true;
  }

  public enum Authorization {
    ALLOW,
    DENY
  }
}

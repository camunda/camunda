/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.physicaltenant;

import io.camunda.zeebe.gateway.interceptors.InterceptorUtil;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.CompleteJobRequest;
import io.camunda.zeebe.gateway.query.QueryApi;
import io.camunda.zeebe.test.util.grpc.CloseAwareListener;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCall.Listener;
import io.grpc.Status;
import java.time.Duration;

/**
 * Listener backing {@link PhysicalTenantQueryServerInterceptor}. On every {@code
 * CompleteJobRequest} it resolves the job's BPMN process id through the interceptor-facing {@link
 * QueryApi} — which routes the underlying broker query to the physical tenant stamped into the gRPC
 * context — and only lets the call proceed if the resolved process id is prefixed with that
 * tenant's id. A failed lookup aborts the call, propagating the query error's message so the test
 * can assert on which engine answered.
 */
public final class PhysicalTenantQueryListener<ReqT> extends CloseAwareListener<ReqT> {

  private final QueryApi api;
  private final ServerCall<ReqT, ?> call;

  public PhysicalTenantQueryListener(
      final Listener<ReqT> delegate, final QueryApi api, final ServerCall<ReqT, ?> call) {
    super(delegate);
    this.api = api;
    this.call = call;
  }

  @Override
  public void onMessage(final ReqT message) {
    if (!(message instanceof CompleteJobRequest)) {
      super.onMessage(message);
      return;
    }

    final var request = (CompleteJobRequest) message;
    final var physicalTenantId = InterceptorUtil.getPhysicalTenantIdKey().get();

    final String processId;
    try {
      processId =
          api.getBpmnProcessIdFromJob(request.getJobKey(), Duration.ofSeconds(5))
              .toCompletableFuture()
              .join();
    } catch (final Exception e) {
      call.close(
          Status.ABORTED.augmentDescription(
              "Query for the job's process id failed: " + rootCauseMessage(e)),
          new Metadata());
      isClosed = true;
      return;
    }

    if (physicalTenantId == null || !processId.startsWith(physicalTenantId)) {
      call.close(
          Status.PERMISSION_DENIED.augmentDescription(
              "Query resolved process id '"
                  + processId
                  + "' which does not belong to physical tenant '"
                  + physicalTenantId
                  + "'"),
          new Metadata());
      isClosed = true;
      return;
    }

    super.onMessage(message);
  }

  private static String rootCauseMessage(final Throwable throwable) {
    var cause = throwable;
    while (cause.getCause() != null) {
      cause = cause.getCause();
    }
    return cause.getMessage();
  }
}

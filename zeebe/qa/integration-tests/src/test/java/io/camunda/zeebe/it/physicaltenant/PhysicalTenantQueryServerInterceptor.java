/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.physicaltenant;

import io.camunda.zeebe.gateway.interceptors.InterceptorUtil;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCall.Listener;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;

/**
 * Test interceptor for {@link PhysicalTenantQueryApiIT}: gates every {@code CompleteJob} call on a
 * {@link io.camunda.zeebe.gateway.query.QueryApi} lookup of the job's BPMN process id (see {@link
 * PhysicalTenantQueryListener}). All other calls pass through untouched.
 *
 * <p>Loaded into the gateway from an external JAR (built via ByteBuddy in the test), so this class
 * and everything it references that is not part of the distribution must be public.
 */
public final class PhysicalTenantQueryServerInterceptor implements ServerInterceptor {

  @Override
  public <ReqT, RespT> Listener<ReqT> interceptCall(
      final ServerCall<ReqT, RespT> call,
      final Metadata headers,
      final ServerCallHandler<ReqT, RespT> next) {
    final var api = InterceptorUtil.getQueryApiKey().get();
    return new PhysicalTenantQueryListener<>(next.startCall(call, headers), api, call);
  }
}

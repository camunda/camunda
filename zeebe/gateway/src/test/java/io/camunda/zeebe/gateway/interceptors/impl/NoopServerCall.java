/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.interceptors.impl;

import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.Status;

public class NoopServerCall<ReqT, RespT> extends ServerCall<ReqT, RespT> {

  @Override
  public void request(final int i) {}

  @Override
  public void sendHeaders(final Metadata metadata) {}

  @Override
  public void sendMessage(final RespT respT) {}

  @Override
  public void close(final Status status, final Metadata metadata) {}

  @Override
  public boolean isCancelled() {
    return false;
  }

  @Override
  public MethodDescriptor<ReqT, RespT> getMethodDescriptor() {
    return null;
  }
}

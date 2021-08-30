/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.impl.interceptor;

import io.camunda.zeebe.gateway.Interceptor.Control;
import io.camunda.zeebe.gateway.Interceptor.Request;
import io.camunda.zeebe.gateway.Interceptor.Status;

public class GrpcControl implements Control {

  private boolean accepted = true;
  private Status status = Status.OK;

  @Override
  public void accept(final Request request) {
    accepted = true;
    status = Status.OK;
  }

  @Override
  public void reject(final Status status, final String content) {
    accepted = false;
    this.status = status;
  }

  public boolean isAccepted() {
    return accepted;
  }

  public io.grpc.Status getStatus() {
    if (accepted) {
      return io.grpc.Status.OK;
    }
    switch (status) {
      case OK:
        return io.grpc.Status.OK;
      case BLOCKED:
        return io.grpc.Status.ABORTED;
      case NOT_FOUND:
        return io.grpc.Status.NOT_FOUND;
      case UNAUTHORIZED:
        return io.grpc.Status.PERMISSION_DENIED;
      default:
        return io.grpc.Status.UNKNOWN;
    }
  }
}

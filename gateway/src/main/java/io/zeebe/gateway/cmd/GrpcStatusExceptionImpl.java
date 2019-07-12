/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.gateway.cmd;

import io.grpc.Status;

/**
 * Simple implementation of {@link GrpcStatusException} when wrapping specific errors for which we
 * want to return a specific {@link Status}.
 */
public class GrpcStatusExceptionImpl extends ClientException implements GrpcStatusException {
  private static final long serialVersionUID = -7333429675023512630L;

  private final Status status;

  public GrpcStatusExceptionImpl(String message, Status status) {
    this(message, status, null);
  }

  public GrpcStatusExceptionImpl(String message, Status status, Throwable cause) {
    super(message, cause);
    this.status = status.augmentDescription(message);
  }

  @Override
  public Status getGrpcStatus() {
    return status;
  }
}

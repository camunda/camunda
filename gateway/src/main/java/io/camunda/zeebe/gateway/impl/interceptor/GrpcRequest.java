/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.impl.interceptor;

import static io.grpc.Metadata.ASCII_STRING_MARSHALLER;

import io.camunda.zeebe.gateway.Interceptor.Request;
import io.grpc.Metadata;

class GrpcRequest implements Request {

  private final Metadata headers;

  public GrpcRequest(final Metadata headers) {
    this.headers = headers;
  }

  @Override
  public String getTarget() {
    return null;
  }

  @Override
  public String getValue(final String name) {
    return headers.get(Metadata.Key.of(name, ASCII_STRING_MARSHALLER));
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import org.springframework.boot.context.properties.NestedConfigurationProperty;

public class Api {

  /** Configuration for long-polling behavior */
  @NestedConfigurationProperty private LongPolling longPolling = new LongPolling();

  /** Configuration for grpc behavior */
  @NestedConfigurationProperty private Grpc grpc = new Grpc();

  /** Configuration for rest behavior */
  @NestedConfigurationProperty private Rest rest = new Rest();

  public LongPolling getLongPolling() {
    return longPolling;
  }

  public void setLongPolling(final LongPolling longPolling) {
    this.longPolling = longPolling;
  }

  public Grpc getGrpc() {
    return grpc;
  }

  public void setGrpc(final Grpc grpc) {
    this.grpc = grpc;
  }

  public Rest getRest() {
    return rest;
  }

  public void setRest(final Rest rest) {
    this.rest = rest;
  }
}

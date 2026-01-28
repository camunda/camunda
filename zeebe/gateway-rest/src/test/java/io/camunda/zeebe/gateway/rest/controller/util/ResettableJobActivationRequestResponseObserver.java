/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.util;

import io.camunda.gateway.protocol.model.JobActivationResult;
import io.camunda.zeebe.gateway.rest.controller.JobActivationRequestResponseObserver;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.ResponseEntity;

public class ResettableJobActivationRequestResponseObserver
    extends JobActivationRequestResponseObserver {

  public ResettableJobActivationRequestResponseObserver(
      final CompletableFuture<ResponseEntity<Object>> result) {
    super(result);
  }

  public void reset() {
    response = new JobActivationResult();
  }

  public ResettableJobActivationRequestResponseObserver setResult(
      final CompletableFuture<ResponseEntity<Object>> result) {
    this.result = result;
    return this;
  }
}

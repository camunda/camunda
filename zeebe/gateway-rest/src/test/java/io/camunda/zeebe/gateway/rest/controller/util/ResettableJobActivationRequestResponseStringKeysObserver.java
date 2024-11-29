/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.util;

import io.camunda.zeebe.gateway.protocol.rest.JobActivationResponseStringKeys;
import io.camunda.zeebe.gateway.rest.controller.JobActivationRequestResponseWithStringKeysObserver;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.ResponseEntity;

public class ResettableJobActivationRequestResponseStringKeysObserver
    extends JobActivationRequestResponseWithStringKeysObserver {

  public ResettableJobActivationRequestResponseStringKeysObserver(
      final CompletableFuture<ResponseEntity<Object>> result) {
    super(result);
  }

  public void reset() {
    response = new JobActivationResponseStringKeys();
  }

  public ResettableJobActivationRequestResponseStringKeysObserver setResult(
      final CompletableFuture<ResponseEntity<Object>> result) {
    this.result = result;
    return this;
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.security.identity;

import io.camunda.identity.sdk.impl.rest.exception.RestException;
import io.camunda.operate.util.RetryOperation;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
public class IdentityRetryService {

  public <T> T requestWithRetry(
      final RetryOperation.RetryConsumer<T> retryConsumer, final String operationName)
      throws Exception {
    return RetryOperation.<T>newBuilder()
        .noOfRetry(10)
        .delayInterval(500, TimeUnit.MILLISECONDS)
        .retryOn(RestException.class)
        .retryConsumer(retryConsumer)
        .message(operationName)
        .build()
        .retry();
  }
}

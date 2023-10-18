/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.security.identity;

import io.camunda.identity.sdk.exception.IdentityException;
import io.camunda.operate.util.RetryOperation;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class IdentityRetryService {

  public <T> T requestWithRetry(final RetryOperation.RetryConsumer<T> retryConsumer, final String operationName)
          throws Exception {
    return RetryOperation.<T>newBuilder()
            .noOfRetry(10)
            .delayInterval(500, TimeUnit.MILLISECONDS)
            .retryOn(IdentityException.class)
            .retryConsumer(retryConsumer)
            .message(operationName)
            .build()
            .retry();
  }
}

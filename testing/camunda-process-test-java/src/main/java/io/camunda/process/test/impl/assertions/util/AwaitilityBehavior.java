/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.test.impl.assertions.util;

import static org.assertj.core.api.Assertions.fail;

import io.camunda.client.api.command.ClientException;
import io.camunda.process.test.api.CamundaAssert;
import io.camunda.process.test.api.CamundaAssertAwaitBehavior;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionTimeoutException;
import org.junit.function.ThrowingRunnable;

public class AwaitilityBehavior implements CamundaAssertAwaitBehavior {

  private Duration assertionTimeout = CamundaAssert.DEFAULT_ASSERTION_TIMEOUT;
  private Duration assertionInterval = CamundaAssert.DEFAULT_ASSERTION_INTERVAL;

  @Override
  public void untilAsserted(final ThrowingRunnable assertion) throws AssertionError {
    // If await() times out, the exception doesn't contain the assertion error. Use a reference to
    // store the error's failure message.
    final AtomicReference<String> failureMessage = new AtomicReference<>("<no assertion error>");
    try {
      Awaitility.await()
          .timeout(assertionTimeout)
          .pollInterval(assertionInterval)
          .ignoreException(ClientException.class)
          .untilAsserted(
              () -> {
                try {
                  assertion.run();
                } catch (final AssertionError e) {
                  failureMessage.set(e.getMessage());
                  throw e;
                }
              });

    } catch (final ConditionTimeoutException ignore) {
      fail(failureMessage.get());
    }
  }

  @Override
  public void setAssertionTimeout(final Duration assertionTimeout) {
    this.assertionTimeout = assertionTimeout;
  }

  @Override
  public void setAssertionInterval(final Duration assertionInterval) {
    this.assertionInterval = assertionInterval;
  }
}

/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.process.test.impl.assertions.util;

import static org.assertj.core.api.Assertions.fail;

import io.camunda.client.api.command.ClientException;
import io.camunda.process.test.api.CamundaAssert;
import io.camunda.process.test.api.CamundaAssertAwaitBehavior;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.assertj.core.api.SoftAssertionsProvider.ThrowingRunnable;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionTimeoutException;

public class AwaitilityBehavior implements CamundaAssertAwaitBehavior {

  private static final String TIMEOUT_FAILURE_MESSAGE =
      "<No assertion error occurred. Maybe, the assertion timed out before it could be tested.>";

  private static final String UNEXPECTED_FAILURE_MESSAGE =
      "<No assertion error occurred, but an unexpected exception was thrown. Check the cause for details.>";

  private static final ThreadLocal<Duration> TIMEOUT_OVERRIDE = new ThreadLocal<>();
  private static final ThreadLocal<Duration> INTERVAL_OVERRIDE = new ThreadLocal<>();

  private Duration assertionTimeout = CamundaAssert.DEFAULT_ASSERTION_TIMEOUT;
  private Duration assertionInterval = CamundaAssert.DEFAULT_ASSERTION_INTERVAL;

  public static void setConditionProbeOverrides(final Duration timeout, final Duration interval) {
    TIMEOUT_OVERRIDE.set(timeout);
    INTERVAL_OVERRIDE.set(interval);
  }

  public static void clearConditionProbeOverrides() {
    TIMEOUT_OVERRIDE.remove();
    INTERVAL_OVERRIDE.remove();
  }

  @Override
  public void untilAsserted(final ThrowingRunnable assertion) throws AssertionError {
    final Duration effectiveTimeout =
        TIMEOUT_OVERRIDE.get() != null ? TIMEOUT_OVERRIDE.get() : assertionTimeout;

    // Fast path for condition probing: single check, no Awaitility overhead
    if (effectiveTimeout.isZero()) {
      try {
        assertion.run();
      } catch (final Throwable t) {
        if (t instanceof AssertionError) {
          throw (AssertionError) t;
        }
        throw new AssertionError(t.getMessage(), t);
      }
      return;
    }

    // If await() times out, the exception doesn't contain the assertion error. Use a reference to
    // store the error's failure message.
    final AtomicReference<String> failureMessage = new AtomicReference<>();
    final AtomicReference<Throwable> unexpectedException = new AtomicReference<>();
    try {
      final Duration effectiveInterval =
          INTERVAL_OVERRIDE.get() != null ? INTERVAL_OVERRIDE.get() : assertionInterval;
      Awaitility.await()
          .timeout(effectiveTimeout)
          .pollInterval(effectiveInterval)
          .ignoreExceptionsInstanceOf(ClientException.class)
          .untilAsserted(
              () -> {
                try {
                  assertion.run();
                } catch (final AssertionError e) {
                  failureMessage.set(e.getMessage());
                  throw e;
                } catch (final Exception unexpected) {
                  unexpectedException.set(unexpected);
                  throw unexpected;
                }
              });

    } catch (final ConditionTimeoutException ignore) {
      if (failureMessage.get() != null) {
        fail(failureMessage.get());
      } else if (unexpectedException.get() != null) {
        fail(UNEXPECTED_FAILURE_MESSAGE, unexpectedException.get());
      } else {
        fail(TIMEOUT_FAILURE_MESSAGE);
      }
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

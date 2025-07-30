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
package io.camunda.process.test.utils;

import static org.assertj.core.api.Assertions.fail;

import io.camunda.client.api.command.ClientException;
import io.camunda.process.test.api.CamundaAssert;
import io.camunda.process.test.api.CamundaAssertAwaitBehavior;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.assertj.core.api.SoftAssertionsProvider.ThrowingRunnable;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionTimeoutException;
import org.awaitility.core.TerminalFailureException;

public final class DevAwaitBehavior implements CamundaAssertAwaitBehavior {

  private static final String INITIAL_FAILURE_MESSAGE =
      "<No assertion error occurred. Maybe, the assertion timed out before it could be tested.>";

  private final boolean expectFailure;

  private Duration assertionTimeout = CamundaAssert.DEFAULT_ASSERTION_TIMEOUT;
  private Duration assertionInterval = Duration.ZERO;

  private DevAwaitBehavior(final boolean expectFailure) {
    this.expectFailure = expectFailure;
  }

  public static DevAwaitBehavior expectFailure() {
    return new DevAwaitBehavior(true);
  }

  public static DevAwaitBehavior expectSuccess() {
    return new DevAwaitBehavior(false);
  }

  @Override
  public void untilAsserted(final ThrowingRunnable assertion) throws AssertionError {
    final AtomicReference<String> failureMessage = new AtomicReference<>(INITIAL_FAILURE_MESSAGE);
    final AtomicBoolean failFastCondition = new AtomicBoolean(false);

    try {
      Awaitility.await()
          .failFast(failFastCondition::get)
          .timeout(assertionTimeout)
          .pollInterval(assertionInterval)
          .ignoreException(ClientException.class)
          .untilAsserted(
              () -> {
                try {
                  assertion.run();
                } catch (final AssertionError e) {
                  failureMessage.set(e.getMessage());

                  if (expectFailure) {
                    failFastCondition.set(true);
                  }
                  throw e;
                }
              });
    } catch (final ConditionTimeoutException | TerminalFailureException ignore) {
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

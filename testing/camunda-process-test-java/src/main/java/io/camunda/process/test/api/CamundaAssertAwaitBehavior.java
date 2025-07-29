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
package io.camunda.process.test.api;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import org.assertj.core.api.SoftAssertionsProvider.ThrowingRunnable;

/**
 * A behavior for waiting until a given assertion is satisfied.
 *
 * <p>It is used to compensate the delay for assertions caused by the asynchronous processing of
 * Camunda.
 */
public interface CamundaAssertAwaitBehavior {

  /**
   * Wait until the given assertion is satisfied.
   *
   * @param assertion the assertion to be satisfied
   * @throws AssertionError if the assertion is not satisfied
   */
  void untilAsserted(final ThrowingRunnable assertion) throws AssertionError;

  /**
   * Wait until the given assertion is satisfied.
   *
   * @param supplier supplies the value for the assertion
   * @param assertion the assertion to be satisfied
   * @param <T> the value type
   * @throws AssertionError if the assertion is not satisfied
   */
  default <T> void untilAsserted(final Callable<T> supplier, final Consumer<T> assertion)
      throws AssertionError {
    untilAsserted(
        () -> {
          final T value = supplier.call();
          assertion.accept(value);
        });
  }

  /**
   * Set the timeout of the assertion.
   *
   * @param assertionTimeout the assertion's timeout
   */
  void setAssertionTimeout(final Duration assertionTimeout);

  /**
   * Set the interval between the assertion attempts.
   *
   * @param assertionInterval the assertion's interval
   */
  void setAssertionInterval(final Duration assertionInterval);
}

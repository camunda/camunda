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

import io.camunda.process.test.api.CamundaAssertAwaitBehavior;
import java.time.Duration;
import org.assertj.core.api.SoftAssertionsProvider.ThrowingRunnable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link CamundaAssertAwaitBehavior} that evaluates the assertion exactly once, without any
 * polling or timeout.
 */
public class InstantProbeAwaitBehavior implements CamundaAssertAwaitBehavior {

  private static final Logger LOGGER = LoggerFactory.getLogger(InstantProbeAwaitBehavior.class);

  @Override
  public void untilAsserted(final ThrowingRunnable assertion) throws AssertionError {
    try {
      assertion.run();
    } catch (final AssertionError e) {
      throw e;
    } catch (final Throwable t) {
      LOGGER.debug("Instant probe caught unexpected exception", t);
      throw new AssertionError(t.getMessage(), t);
    }
  }

  @Override
  public Duration getAssertionInterval() {
    return Duration.ZERO;
  }

  @Override
  public void setAssertionInterval(final Duration assertionInterval) {
    // no-op — instant probing has no interval
  }

  @Override
  public Duration getAssertionTimeout() {
    return Duration.ZERO;
  }

  @Override
  public void setAssertionTimeout(final Duration assertionTimeout) {
    // no-op — instant probing has no timeout
  }

  @Override
  public CamundaAssertAwaitBehavior withAssertionTimeout(final Duration assertionTimeout) {
    // ignore - instant probing has no timeout
    return new InstantProbeAwaitBehavior();
  }
}

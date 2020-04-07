/*
 * Copyright 2017-present Open Networking Foundation
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atomix.utils.concurrent;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/** Retrying function test. */
public class RetryingFunctionTest {
  private int round;

  @Before
  public void setUp() {
    round = 1;
  }

  @After
  public void tearDown() {
    round = 0;
  }

  @Test(expected = RetryableException.class)
  public void testNoRetries() {
    new RetryingFunction<>(this::succeedAfterOneFailure, RetryableException.class, 0, 10)
        .apply(null);
  }

  @Test
  public void testSuccessAfterOneRetry() {
    new RetryingFunction<>(this::succeedAfterOneFailure, RetryableException.class, 1, 10)
        .apply(null);
  }

  @Test(expected = RetryableException.class)
  public void testFailureAfterOneRetry() {
    new RetryingFunction<>(this::succeedAfterTwoFailures, RetryableException.class, 1, 10)
        .apply(null);
  }

  @Test
  public void testFailureAfterTwoRetries() {
    new RetryingFunction<>(this::succeedAfterTwoFailures, RetryableException.class, 2, 10)
        .apply(null);
  }

  @Test(expected = NonRetryableException.class)
  public void testFailureWithNonRetryableFailure() {
    new RetryingFunction<>(this::failCompletely, RetryableException.class, 2, 10).apply(null);
  }

  private String succeedAfterOneFailure(final String input) {
    if (round++ <= 1) {
      throw new RetryableException();
    } else {
      return "pass";
    }
  }

  private String succeedAfterTwoFailures(final String input) {
    if (round++ <= 2) {
      throw new RetryableException();
    } else {
      return "pass";
    }
  }

  private String failCompletely(final String input) {
    if (round++ <= 1) {
      throw new NonRetryableException();
    } else {
      return "pass";
    }
  }

  private static class RetryableException extends RuntimeException {}

  private static class NonRetryableException extends RuntimeException {}
}

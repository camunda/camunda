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
package io.camunda.zeebe.client.impl;

import io.camunda.zeebe.client.CredentialsProvider.StatusCode;
import io.camunda.zeebe.client.api.command.ClientException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class RetriableClientFutureImplTest {

  public static final Predicate<StatusCode> SHOULD_RETRY_ALWAYS = ignore -> true;

  @Test
  void shouldNotRetryOnNext() {
    // given
    final RetriableClientFutureImpl<Object, Object> future =
        new RetriableClientFutureImpl<>(
            // even when instructed to retry
            SHOULD_RETRY_ALWAYS,
            ignore ->
                // then
                Assertions.fail("Expect to not retry"));

    // when
    future.onNext(null);
  }

  @Test
  void shouldRetryOnError() {
    // given
    final AtomicBoolean isRetried = new AtomicBoolean(false);
    final RetriableClientFutureImpl<Object, Object> future =
        new RetriableClientFutureImpl<>(
            SHOULD_RETRY_ALWAYS,
            observer -> {
              isRetried.set(true);
              observer.onError(new ClientException("An error occurred again"));
            });

    // when
    future.onError(new ClientException("An error occurred"));

    // then
    Assertions.assertThat(isRetried).isTrue();
  }

  @Test
  void shouldRetryOnErrorOnlyTwice() {
    // given
    final AtomicInteger numberOfRetries = new AtomicInteger(0);
    final RetriableClientFutureImpl<Object, Object> future =
        new RetriableClientFutureImpl<>(
            // even when instructed to always retry
            SHOULD_RETRY_ALWAYS,
            observer -> {
              numberOfRetries.incrementAndGet();
              observer.onError(new ClientException("An error occurred again"));
            });

    // when
    future.onError(new ClientException("An error occurred"));

    // then
    Assertions.assertThat(numberOfRetries.get())
        .describedAs("Expected to retry twice")
        .isEqualTo(2);
  }

  @Test
  void shouldRetryOnErrorOnlyWhenRetryPrecidateTestsTrue() {
    // given
    final AtomicInteger numberOfRetries = new AtomicInteger(0);
    final RetriableClientFutureImpl<Object, Object> future =
        new RetriableClientFutureImpl<>(
            // when instructed to retry only once
            ignore -> numberOfRetries.get() < 1,
            observer -> {
              numberOfRetries.incrementAndGet();
              observer.onError(new ClientException("An error occurred again"));
            });

    // when
    future.onError(new ClientException("An error occurred"));

    // then
    Assertions.assertThat(numberOfRetries.get())
        .describedAs("Expected to retry only once")
        .isEqualTo(1);
  }
}

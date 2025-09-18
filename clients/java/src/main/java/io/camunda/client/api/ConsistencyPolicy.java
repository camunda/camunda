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
package io.camunda.client.api;

import java.time.Duration;
import java.util.function.Predicate;

public interface ConsistencyPolicy<T> {
  ConsistencyPolicy<T> predicate(final Predicate<T> predicate);

  ConsistencyPolicy<T> retryBackoff(final Duration retryBackoff);

  ConsistencyPolicy<T> waitUpTo(final Duration waitUpTo);

  Predicate<T> getPredicate();

  Duration getRetryBackoff();

  Duration getWaitUpTo();

  static <T> ConsistencyPolicy<T> noWait() {
    return new ConsistencyPolicy<T>() {
      @Override
      public ConsistencyPolicy<T> predicate(final Predicate<T> predicate) {
        return this;
      }

      @Override
      public ConsistencyPolicy<T> retryBackoff(final Duration retryBackoff) {
        return this;
      }

      @Override
      public ConsistencyPolicy<T> waitUpTo(final Duration waitUpTo) {
        return this;
      }

      @Override
      public Predicate<T> getPredicate() {
        return r -> true;
      }

      @Override
      public Duration getRetryBackoff() {
        return Duration.ZERO;
      }

      @Override
      public Duration getWaitUpTo() {
        return Duration.ZERO;
      }
    };
  }
}

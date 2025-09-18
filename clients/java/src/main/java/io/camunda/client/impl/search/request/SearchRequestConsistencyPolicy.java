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
package io.camunda.client.impl.search.request;

import io.camunda.client.api.ConsistencyPolicy;
import io.camunda.client.api.search.response.SearchResponse;
import java.time.Duration;
import java.util.Objects;
import java.util.function.Predicate;

public class SearchRequestConsistencyPolicy<T extends SearchResponse<?>>
    implements ConsistencyPolicy<T> {
  private Predicate<T> predicate;
  private Duration retryBackoff;
  private Duration waitUpTo;

  public SearchRequestConsistencyPolicy() {
    predicate = res -> !res.items().isEmpty();
    retryBackoff = Duration.ofMillis(500);
    waitUpTo = Duration.ofSeconds(10L);
  }

  @Override
  public SearchRequestConsistencyPolicy<T> predicate(final Predicate<T> predicate) {
    Objects.requireNonNull(predicate, "predicate is required");
    this.predicate = predicate;
    return this;
  }

  @Override
  public SearchRequestConsistencyPolicy<T> retryBackoff(final Duration retryBackoff) {
    Objects.requireNonNull(retryBackoff, "retryBackoff is required");
    this.retryBackoff = retryBackoff;
    return this;
  }

  @Override
  public SearchRequestConsistencyPolicy<T> waitUpTo(final Duration waitUpTo) {
    Objects.requireNonNull(waitUpTo, "waitUpTo is required");
    this.waitUpTo = waitUpTo;
    return this;
  }

  @Override
  public Predicate<T> getPredicate() {
    return predicate;
  }

  @Override
  public Duration getRetryBackoff() {
    return retryBackoff;
  }

  @Override
  public Duration getWaitUpTo() {
    return waitUpTo;
  }
}

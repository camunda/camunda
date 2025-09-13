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
package io.camunda.client.api.search.request;

import io.camunda.client.api.command.ClientException;
import io.camunda.client.api.command.FinalCommandStep;
import io.camunda.client.api.search.response.SearchResponse;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;

public interface FinalSearchRequestStep<T> extends FinalCommandStep<SearchResponse<T>> {

  @Override
  FinalSearchRequestStep<T> requestTimeout(Duration requestTimeout);

  /**
   * Sends the search request to the Camunda gateway and returns the response. This operation is
   * synchronous.
   *
   * <p>Search requests are eventually consistent. The consistency management parameter defines how
   * to deal with that.
   *
   * @param consistencyManagement - the consistency management strategy to use for this search
   *     request
   * @return the response of the search request
   */
  default SearchResponse<T> execute(
      final Consumer<ConsistencyManagement<SearchResponse<T>>> consistencyManagement) {
    final ConsistencyManagement<SearchResponse<T>> management = new ConsistencyManagement<>();
    consistencyManagement.accept(management);

    final long start = System.currentTimeMillis();
    final long end = start + management.getWaitUpTo().toMillis();
    // execute once first to avoid waiting if not necessary
    SearchResponse<T> res = execute();
    if (management.getPredicate().test(res)) {
      return res;
    }

    // then retry until timeout
    while (System.currentTimeMillis() < end) {
      res = send().join(management.retryBackoff.toMillis(), TimeUnit.MILLISECONDS);
      if (management.getPredicate().test(res)) {
        return res;
      }
    }
    throw new ClientException(
        String.format(
            "Condition for search response not fulfilled within %s", management.getWaitUpTo()));
  }

  class ConsistencyManagement<T extends SearchResponse<?>> {
    private Predicate<T> predicate;
    private Duration retryBackoff;
    private Duration waitUpTo;

    public ConsistencyManagement() {
      predicate = res -> !res.items().isEmpty();
      retryBackoff = Duration.ofMillis(500);
      waitUpTo = Duration.ofSeconds(10L);
    }

    public ConsistencyManagement<T> predicate(final Predicate<T> predicate) {
      Objects.requireNonNull(predicate, "predicate is required");
      this.predicate = predicate;
      return this;
    }

    public Predicate<T> getPredicate() {
      return predicate;
    }

    public ConsistencyManagement<T> retryBackoff(final Duration retryBackoff) {
      Objects.requireNonNull(retryBackoff, "retryBackoff is required");
      this.retryBackoff = retryBackoff;
      return this;
    }

    public Duration getRetryBackoff() {
      return retryBackoff;
    }

    public ConsistencyManagement<T> waitUpTo(final Duration waitUpTo) {
      Objects.requireNonNull(waitUpTo, "waitUpTo is required");
      this.waitUpTo = waitUpTo;
      return this;
    }

    public Duration getWaitUpTo() {
      return waitUpTo;
    }
  }
}

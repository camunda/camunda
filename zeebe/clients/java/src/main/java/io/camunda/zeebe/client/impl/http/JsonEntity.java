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
package io.camunda.zeebe.client.impl.http;

import io.camunda.zeebe.gateway.protocol.rest.ProblemDetail;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * Represents a possible response entity from the Zeebe REST API, either a successful response of
 * type {@link T}, or a {@link ProblemDetail} returned by the server.
 *
 * @param <T> the type of the successful response
 */
interface JsonEntity<T> {

  /**
   * @return a successful response if any
   * @throws NoSuchElementException if there is no successful response; this indicates the result is
   *     in fact a problem (see {@link #problem()})
   */
  T response();

  /**
   * @return a problem returned by the server if any
   * @throws NoSuchElementException if this is, in fact, a successful response (see {@link
   *     #response()})
   */
  ProblemDetail problem();

  /**
   * @return true if there is a response, false otherwise; always returns the opposite of {@link
   *     #isProblem()}
   */
  boolean isResponse();

  /**
   * @return true if there is a problem, false otherwise; always returns the opposite of {@link
   *     #isResponse()}
   */
  boolean isProblem();

  static <T> JsonEntity<T> of(final T response) {
    return new Response<>(response);
  }

  static <T> JsonEntity<T> of(final ProblemDetail error) {
    return new Error<>(error);
  }

  final class Response<T> implements JsonEntity<T> {
    private final T response;

    private Response(final T response) {
      this.response = Objects.requireNonNull(response, "must specify a response");
    }

    @Override
    public T response() {
      return response;
    }

    @Override
    public ProblemDetail problem() {
      throw new NoSuchElementException(
          "Expected to get a problem, but this is a successful response; use #response()");
    }

    @Override
    public boolean isResponse() {
      return true;
    }

    @Override
    public boolean isProblem() {
      return false;
    }
  }

  final class Error<T> implements JsonEntity<T> {
    private final ProblemDetail problem;

    private Error(final ProblemDetail problem) {
      this.problem = Objects.requireNonNull(problem, "must specify a problem");
    }

    @Override
    public T response() {
      throw new NoSuchElementException(
          "Expected to get a response, but this is a problem; use #problem()");
    }

    @Override
    public ProblemDetail problem() {
      return problem;
    }

    @Override
    public boolean isResponse() {
      return false;
    }

    @Override
    public boolean isProblem() {
      return true;
    }
  }
}

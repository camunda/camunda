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
package io.camunda.client.impl.http;

import io.camunda.client.protocol.rest.ProblemDetail;
import java.nio.ByteBuffer;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * Represents a possible response entity from the Camunda REST API, either a successful response of
 * type {@link T}, or a {@link ProblemDetail} returned by the server.
 *
 * @param <T> the type of the successful response
 */
public interface ApiEntity<T> {

  /**
   * @return a successful response if any
   * @throws NoSuchElementException if this is any other API entity
   */
  T response();

  /**
   * @return a problem returned by the server if any
   * @throws NoSuchElementException if this is any other API entity
   */
  ProblemDetail problem();

  /**
   * @return a raw byte buffer of the consumed body returned by the server, e.g. when it returns a
   *     body with unknown content type
   * @throws NoSuchElementException if this is any other API entity
   */
  ByteBuffer unknown();

  /**
   * @return true if there is a response, false otherwise; always returns the opposite of {@link
   *     #isProblem()}
   */
  default boolean isResponse() {
    return false;
  }

  /**
   * @return true if there is a problem, false otherwise; always returns the opposite of {@link
   *     #isResponse()}
   */
  default boolean isProblem() {
    return false;
  }

  /**
   * @return true if the response is neither a problem nor an expected JSON body
   */
  default boolean isUnknown() {
    return false;
  }

  static <T> ApiEntity<T> of(final T response) {
    return new Response<>(response);
  }

  static <T> ApiEntity<T> of(final ProblemDetail error) {
    return new Error<>(error);
  }

  static <T> ApiEntity<T> of(final ByteBuffer body) {
    return new Unknown<>(body);
  }

  final class Response<T> implements ApiEntity<T> {
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
    public ByteBuffer unknown() {
      throw new NoSuchElementException(
          "Expected to get an unknown body, but this is a successful response; use #response()");
    }

    @Override
    public boolean isResponse() {
      return true;
    }
  }

  final class Error<T> implements ApiEntity<T> {
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
    public ByteBuffer unknown() {
      throw new NoSuchElementException(
          "Expected to get an unknown body, but this is a problem; use #problem()");
    }

    @Override
    public boolean isProblem() {
      return true;
    }
  }

  final class Unknown<T> implements ApiEntity<T> {
    private final ByteBuffer body;

    private Unknown(final ByteBuffer body) {
      this.body = body;
    }

    @Override
    public T response() {
      throw new NoSuchElementException(
          "Expected to get a response, but this is an unknown body; use #unknown()");
    }

    @Override
    public ProblemDetail problem() {
      throw new NoSuchElementException(
          "Expected to get a response, but this is an unknown body; use #unknown()");
    }

    @Override
    public ByteBuffer unknown() {
      return body;
    }

    @Override
    public boolean isUnknown() {
      return true;
    }
  }
}

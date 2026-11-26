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

/**
 * Tunes how the client reacts when the cluster signals backpressure, i.e. when it rejects a request
 * because it is temporarily overloaded: an HTTP 429, an HTTP 503 or 500 whose problem title or
 * detail contains {@code RESOURCE_EXHAUSTED}, or a gRPC status {@code RESOURCE_EXHAUSTED}.
 *
 * <p>Unless the profile is {@link #DISABLED}, such a rejection does not fail the call right away.
 * The client retries it (3 attempts in total, exponential backoff with full jitter, starting at 100
 * ms and capped at 2 s) as long as the request timeout allows it. Except for {@link #LEGACY}, the
 * client additionally limits the number of concurrently running requests while the cluster is under
 * pressure and lifts the limit again once the cluster has recovered. Only requests that start new
 * work are limited: requests that complete, fail or throw an error for a job, complete a user task,
 * read data (GET requests and searches) and job activation are never delayed by the limit.
 *
 * <p>The profiles mirror those of the Camunda TypeScript SDK, with the same names and values.
 *
 * @see io.camunda.client.CamundaClientBuilder#backpressureProfile(BackpressureProfile)
 */
public enum BackpressureProfile {

  /**
   * The default. The limit starts from 16 concurrent requests, each rejection multiplies it by 0.7
   * (by 0.5 from the third consecutive rejection on), never below 1. It grows again by one per
   * second while the cluster is quiet, then by a factor of 1.5, and is lifted after 30 seconds
   * without rejection.
   */
  BALANCED,

  /**
   * Backs off harder and recovers more slowly than {@link #BALANCED}. Suited to shared clusters:
   * the limit starts from 12 concurrent requests, each rejection multiplies it by 0.6 (by 0.4 from
   * the second consecutive rejection on), and it is lifted only after 60 seconds without rejection.
   */
  CONSERVATIVE,

  /**
   * Backs off more gently and recovers faster than {@link #BALANCED}. Suited to dedicated clusters
   * with known headroom: the limit starts from 24 concurrent requests, each rejection multiplies it
   * by 0.8 (by 0.6 from the fourth consecutive rejection on), never below 2, and it is lifted after
   * 15 seconds without rejection.
   */
  AGGRESSIVE,

  /**
   * Retries rejected requests with backoff like the other profiles, but never limits concurrency.
   *
   * <p>The name is taken from the TypeScript SDK, where it stands for "per-call retry only". It
   * does not restore the behavior of the client before backpressure handling existed; use {@link
   * #DISABLED} for that.
   */
  LEGACY,

  /**
   * Turns client-side backpressure handling off: no retry with backoff, no concurrency limit.
   * Requests are rejected right away, as before the feature existed, except for the single retry
   * that the underlying HTTP client performs for HTTP 429 and 503 responses.
   */
  DISABLED
}

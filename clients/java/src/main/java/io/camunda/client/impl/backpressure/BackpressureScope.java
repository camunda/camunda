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
package io.camunda.client.impl.backpressure;

/** How a request takes part in the client-side backpressure handling. */
public enum BackpressureScope {

  /**
   * The request waits for the concurrency limit, is retried when the cluster rejects it because of
   * backpressure and feeds the limit. This is the default.
   */
  MANAGED,

  /**
   * Like {@link #MANAGED}, but the request never waits for the concurrency limit. Used for requests
   * that finish work that is already running (completing, failing or throwing an error for a job,
   * completing a user task) and for requests that only read: delaying the former would hold on to
   * work the cluster wants to get rid of, and the latter put no load on the part of the cluster
   * that signals backpressure.
   */
  UNGATED,

  /**
   * The request is not touched at all. Used for job activation, where the job worker already backs
   * off between polls and a retry of a long poll would only duplicate that.
   */
  UNMANAGED
}

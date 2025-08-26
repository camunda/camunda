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
package io.camunda.client.api.response;

/**
 * Response for cluster status request. The status endpoint returns 204 (No Content) when healthy
 * and 503 (Service Unavailable) when unhealthy. The response body is always empty.
 */
public interface StatusResponse {
  /**
   * @return {@link Status#UP} if the cluster is healthy (has at least one partition with a healthy
   *     leader), {@link Status#DOWN} otherwise
   */
  Status getStatus();

  enum Status {
    UP,
    DOWN
  }
}

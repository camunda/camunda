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
package io.camunda.client.health;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.StatusResponse;

public class HealthCheck {

  private final CamundaClient camundaClient;

  public HealthCheck(final CamundaClient camundaClient) {
    this.camundaClient = camundaClient;
  }

  /**
   * Does not catch failures itself: on connectivity issues or timeouts, {@code
   * newStatusRequest().execute()} throws (e.g. {@code ClientException}/{@code
   * ClientStatusException}), and this method lets that propagate. This relies on the caller mapping
   * the failure to a degraded status instead of surfacing it as-is — e.g. Spring Boot's {@code
   * AbstractHealthIndicator.health()} catches any exception from {@code doHealthCheck} and reports
   * {@code DOWN} with the exception recorded as the {@code error} detail. Callers that do not offer
   * that safety net must handle the exception themselves.
   *
   * @throws RuntimeException if the status request fails
   */
  public StatusResponse.Status health() {
    return camundaClient.newStatusRequest().execute().getStatus();
  }
}

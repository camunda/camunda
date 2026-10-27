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
package io.camunda.client.impl.response;

import io.camunda.client.api.response.StatusResponse;
import io.camunda.client.protocol.rest.ClusterStatusResponse;

public final class StatusResponseImpl implements StatusResponse {

  private final Status status;

  /**
   * A cluster that is {@code DEGRADED} still processes work, so it maps to {@link Status#UP} just
   * like {@code HEALTHY}. The status code is the fallback when the body is missing or reports an
   * aggregated status this client version does not know.
   */
  public StatusResponseImpl(final ClusterStatusResponse response, final Integer statusCode) {
    if (response == null || response.getStatus() == null) {
      status = statusCode != null && statusCode == 200 ? Status.UP : Status.DOWN;
      return;
    }

    switch (response.getStatus()) {
      case HEALTHY:
      case DEGRADED:
        status = Status.UP;
        break;
      case DOWN:
        status = Status.DOWN;
        break;
      default:
        status = statusCode != null && statusCode == 200 ? Status.UP : Status.DOWN;
    }
  }

  @Override
  public Status getStatus() {
    return status;
  }
}

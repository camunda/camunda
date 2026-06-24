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
import io.camunda.client.api.response.Topology;

public class HealthCheck {
  private final CamundaClient camundaClient;

  public HealthCheck(final CamundaClient camundaClient) {
    this.camundaClient = camundaClient;
  }

  public CheckResult health() {
    final Topology topology = camundaClient.newTopologyRequest().send().join();
    if (topology.getBrokers().isEmpty()) {
      return CheckResult.DOWN;
    } else {
      return CheckResult.UP;
    }
  }

  public enum CheckResult {
    UP,
    DOWN
  }
}

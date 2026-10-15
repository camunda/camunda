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
package io.camunda.client.impl.metrics;

import io.camunda.client.api.metrics.CamundaClientMetrics;

/**
 * The implementation behind every {@link CamundaClientMetrics}: creates the metrics of one client
 * once its physical tenant, which tags all its meters, is known.
 *
 * <p>Like {@link ClientMetrics}, it must not mention any Micrometer class, as the client builder
 * refers to it.
 */
@FunctionalInterface
public interface ClientMetricsFactory extends CamundaClientMetrics {

  ClientMetrics create(String physicalTenantId);

  static ClientMetricsFactory noop() {
    return physicalTenantId -> ClientMetrics.noop();
  }
}

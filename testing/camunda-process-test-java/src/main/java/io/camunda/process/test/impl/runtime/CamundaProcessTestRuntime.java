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
package io.camunda.process.test.impl.runtime;

import io.camunda.client.api.response.Topology;
import io.camunda.process.test.api.CamundaClientBuilderFactory;
import java.net.URI;
import java.time.Duration;

public interface CamundaProcessTestRuntime extends AutoCloseable {

  void start();

  URI getCamundaRestApiAddress();

  URI getCamundaGrpcApiAddress();

  URI getCamundaMonitoringApiAddress();

  URI getConnectorsRestApiAddress();

  CamundaClientBuilderFactory getCamundaClientBuilderFactory();

  /**
   * Waits until the runtime is ready (all partitions healthy, at least one partition available).
   * Throws a {@link RuntimeException} if the runtime does not become ready within the timeout.
   *
   * @param timeout maximum time to wait
   * @return the cluster {@link Topology} once ready
   */
  Topology waitUntilClusterReady(final Duration timeout);
}

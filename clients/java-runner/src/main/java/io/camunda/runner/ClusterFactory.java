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
package io.camunda.runner;

import io.camunda.client.CamundaClient;
import io.camunda.runner.internal.LocalContainerCluster;
import io.camunda.runner.internal.LocalhostCluster;
import io.camunda.runner.internal.UsingCluster;

/**
 * Factory for {@link Cluster} instances. Reach via {@link LiveBpmn#cluster()}.
 *
 * <p>Phase 2 supports {@link #testcontainer()}, {@link #localhost()}, {@link #localhost(int)}, and
 * {@link #using(CamundaClient)}. {@code properties()} and {@code auto()} are deferred to Phase 3.
 */
public final class ClusterFactory {

  /** Package-private; reach via {@link LiveBpmn#cluster()}. */
  ClusterFactory() {}

  /** Boots a Camunda Testcontainer; client connects via gRPC plaintext + REST. */
  public Cluster testcontainer() {
    return new LocalContainerCluster();
  }

  /** Connects to a Camunda gateway on {@code localhost:26500}. */
  public Cluster localhost() {
    return localhost(26500);
  }

  /** Connects to a Camunda gateway on {@code localhost:port}. */
  public Cluster localhost(final int port) {
    return new LocalhostCluster(port);
  }

  /**
   * Wraps a user-provided {@link CamundaClient}. The runner will not close the client on {@link
   * Cluster#close()}.
   */
  public Cluster using(final CamundaClient client) {
    return new UsingCluster(client);
  }
}

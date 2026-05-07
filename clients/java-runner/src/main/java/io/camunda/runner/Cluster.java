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

/**
 * A handle to a Camunda cluster the runner can deploy to. The {@link CamundaClient} is materialised
 * lazily on first {@link #client()} call and cached for the lifetime of the cluster.
 *
 * <p>{@link #ownsClient()} returns {@code true} when the cluster created (and therefore owns) the
 * client; {@link #close()} on such a cluster shuts everything down. For {@link
 * ClusterFactory#using(CamundaClient)} the runner does not own the client and {@code close()} is a
 * no-op.
 */
public interface Cluster extends AutoCloseable {

  /** Returns the (possibly lazily-built) {@link CamundaClient} for this cluster. */
  CamundaClient client();

  /** Whether the runner owns the client lifecycle (i.e. should close it). */
  boolean ownsClient();

  @Override
  void close();
}

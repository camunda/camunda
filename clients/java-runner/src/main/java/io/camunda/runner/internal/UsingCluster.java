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
package io.camunda.runner.internal;

import io.camunda.client.CamundaClient;
import io.camunda.runner.Cluster;
import java.net.URI;

/** Bring-your-own-client cluster. The runner does not close the wrapped client. */
public final class UsingCluster implements Cluster {

  private final CamundaClient client;

  public UsingCluster(final CamundaClient client) {
    this.client = client;
  }

  @Override
  public CamundaClient client() {
    return client;
  }

  @Override
  public boolean ownsClient() {
    return false;
  }

  @Override
  public URI restAddress() {
    final URI configured = client.getConfiguration().getRestAddress();
    return configured != null ? configured : URI.create("http://localhost:8080");
  }

  @Override
  public void close() {
    // user-owned client; no-op
  }
}

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
package io.zeebe.client.impl.response;

import io.zeebe.client.api.response.DeploymentEvent;
import io.zeebe.client.api.response.Process;
import io.zeebe.gateway.protocol.GatewayOuterClass.DeployProcessResponse;
import java.util.List;
import java.util.stream.Collectors;

public final class DeploymentEventImpl implements DeploymentEvent {

  private final long key;
  private final List<Process> processes;

  public DeploymentEventImpl(final DeployProcessResponse response) {
    key = response.getKey();
    processes =
        response.getProcessesList().stream().map(ProcessImpl::new).collect(Collectors.toList());
  }

  @Override
  public long getKey() {
    return key;
  }

  @Override
  public List<Process> getProcesses() {
    return processes;
  }

  @Override
  public String toString() {
    return "DeploymentEventImpl{" + "key=" + key + ", processes=" + processes + '}';
  }
}

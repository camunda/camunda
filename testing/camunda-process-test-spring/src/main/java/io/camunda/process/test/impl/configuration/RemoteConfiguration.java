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
package io.camunda.process.test.impl.configuration;

import io.camunda.client.spring.properties.CamundaClientProperties;
import io.camunda.process.test.impl.runtime.CamundaProcessTestRuntimeDefaults;
import java.net.URI;
import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

public class RemoteConfiguration {

  @NestedConfigurationProperty
  private CamundaClientProperties client = new CamundaClientProperties();

  private URI camundaMonitoringApiAddress =
      CamundaProcessTestRuntimeDefaults.LOCAL_CAMUNDA_MONITORING_API_ADDRESS;

  private URI connectorsRestApiAddress =
      CamundaProcessTestRuntimeDefaults.LOCAL_CONNECTORS_REST_API_ADDRESS;

  private Duration runtimeConnectionTimeout =
      CamundaProcessTestRuntimeDefaults.DEFAULT_REMOTE_RUNTIME_CONNECTION_TIMEOUT;

  public CamundaClientProperties getClient() {
    return client;
  }

  public void setClient(final CamundaClientProperties client) {
    this.client = client;
  }

  public URI getCamundaMonitoringApiAddress() {
    return camundaMonitoringApiAddress;
  }

  public void setCamundaMonitoringApiAddress(final URI camundaMonitoringApiAddress) {
    this.camundaMonitoringApiAddress = camundaMonitoringApiAddress;
  }

  public URI getConnectorsRestApiAddress() {
    return connectorsRestApiAddress;
  }

  public void setConnectorsRestApiAddress(final URI connectorsRestApiAddress) {
    this.connectorsRestApiAddress = connectorsRestApiAddress;
  }

  public Duration getRuntimeConnectionTimeout() {
    return runtimeConnectionTimeout;
  }

  public void setRuntimeConnectionTimeout(final Duration runtimeConnectionTimeout) {
    this.runtimeConnectionTimeout = runtimeConnectionTimeout;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        camundaMonitoringApiAddress, connectorsRestApiAddress, runtimeConnectionTimeout);
  }

  @Override
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final RemoteConfiguration that = (RemoteConfiguration) o;
    return Objects.equals(camundaMonitoringApiAddress, that.camundaMonitoringApiAddress)
        && Objects.equals(connectorsRestApiAddress, that.connectorsRestApiAddress)
        && Objects.equals(runtimeConnectionTimeout, that.runtimeConnectionTimeout);
  }
}

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
package io.camunda.process.test.impl.runtime.properties;

import static io.camunda.process.test.impl.runtime.util.PropertiesUtil.getPropertyOrDefault;

import io.camunda.process.test.impl.runtime.CamundaProcessTestRuntimeDefaults;
import java.net.URI;
import java.util.Properties;

public class RemoteRuntimeProperties {
  public static final String PROPERTY_NAME_REMOTE_CAMUNDA_MONITORING_API_ADDRESS =
      "remote.camundaMonitoringApiAddress";
  public static final String PROPERTY_NAME_REMOTE_CONNECTORS_REST_API_ADDRESS =
      "remote.connectorsRestApiAddress";

  private final URI camundaMonitoringApiAddress;
  private final URI connectorsRestApiAddress;

  private final RemoteRuntimeClientProperties remoteClientProperties;

  public RemoteRuntimeProperties(final Properties properties) {
    camundaMonitoringApiAddress =
        getPropertyOrDefault(
            properties,
            PROPERTY_NAME_REMOTE_CAMUNDA_MONITORING_API_ADDRESS,
            v -> {
              try {
                return URI.create(v);
              } catch (final Throwable t) {
                return CamundaProcessTestRuntimeDefaults.LOCAL_CAMUNDA_MONITORING_API_ADDRESS;
              }
            },
            CamundaProcessTestRuntimeDefaults.LOCAL_CAMUNDA_MONITORING_API_ADDRESS);

    connectorsRestApiAddress =
        getPropertyOrDefault(
            properties,
            PROPERTY_NAME_REMOTE_CONNECTORS_REST_API_ADDRESS,
            v -> {
              try {
                return URI.create(v);
              } catch (final Throwable t) {
                return CamundaProcessTestRuntimeDefaults.LOCAL_CONNECTORS_REST_API_ADDRESS;
              }
            },
            CamundaProcessTestRuntimeDefaults.LOCAL_CONNECTORS_REST_API_ADDRESS);

    remoteClientProperties = new RemoteRuntimeClientProperties(properties);
  }

  public URI getCamundaMonitoringApiAddress() {
    return camundaMonitoringApiAddress;
  }

  public URI getConnectorsRestApiAddress() {
    return connectorsRestApiAddress;
  }

  public RemoteRuntimeClientProperties getRemoteClientProperties() {
    return remoteClientProperties;
  }
}

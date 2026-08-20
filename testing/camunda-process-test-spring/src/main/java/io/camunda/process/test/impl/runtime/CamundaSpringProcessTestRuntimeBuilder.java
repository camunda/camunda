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

import io.camunda.process.test.api.CamundaProcessTestRuntimeMode;
import io.camunda.process.test.impl.configuration.CamundaProcessTestRuntimeConfiguration;

public class CamundaSpringProcessTestRuntimeBuilder {

  public static CamundaProcessTestRuntime buildRuntime(
      final CamundaProcessTestRuntimeBuilder runtimeBuilder,
      final CamundaProcessTestRuntimeConfiguration runtimeConfiguration) {

    final CamundaProcessTestRuntimeMode runtimeMode = runtimeConfiguration.getRuntimeMode();
    runtimeBuilder.withRuntimeMode(runtimeMode);

    if (runtimeMode == null
        || runtimeMode == CamundaProcessTestRuntimeMode.MANAGED
        || runtimeMode == CamundaProcessTestRuntimeMode.SHARED) {
      configureManagedRuntime(runtimeBuilder, runtimeConfiguration);

    } else if (runtimeMode == CamundaProcessTestRuntimeMode.REMOTE) {
      configureRemoteRuntime(runtimeBuilder, runtimeConfiguration);
    }

    return runtimeBuilder.build();
  }

  private static void configureManagedRuntime(
      final CamundaProcessTestRuntimeBuilder runtimeBuilder,
      final CamundaProcessTestRuntimeConfiguration runtimeConfiguration) {

    runtimeBuilder
        .withCamundaDockerImageVersion(runtimeConfiguration.getCamundaDockerImageVersion())
        .withCamundaDockerImageName(runtimeConfiguration.getCamundaDockerImageName())
        .withCamundaEnv(runtimeConfiguration.getCamundaEnvVars())
        .withCamundaLogger(runtimeConfiguration.getCamundaLoggerName())
        .withMultiTenancyEnabled(runtimeConfiguration.isMultiTenancyEnabled());

    runtimeConfiguration.getCamundaExposedPorts().forEach(runtimeBuilder::withCamundaExposedPort);

    runtimeBuilder
        .withConnectorsEnabled(runtimeConfiguration.isConnectorsEnabled())
        .withConnectorsDockerImageName(runtimeConfiguration.getConnectorsDockerImageName())
        .withConnectorsDockerImageVersion(runtimeConfiguration.getConnectorsDockerImageVersion())
        .withConnectorsEnv(runtimeConfiguration.getConnectorsEnvVars())
        .withConnectorsSecrets(runtimeConfiguration.getConnectorsSecrets())
        .withConnectorsLogger(runtimeConfiguration.getConnectorsLoggerName());

    runtimeConfiguration
        .getConnectorsExposedPorts()
        .forEach(runtimeBuilder::withConnectorsExposedPort);
  }

  private static void configureRemoteRuntime(
      final CamundaProcessTestRuntimeBuilder runtimeBuilder,
      final CamundaProcessTestRuntimeConfiguration runtimeConfiguration) {

    runtimeBuilder
        .withRemoteCamundaMonitoringApiAddress(
            runtimeConfiguration.getRemote().getCamundaMonitoringApiAddress())
        .withRemoteConnectorsRestApiAddress(
            runtimeConfiguration.getRemote().getConnectorsRestApiAddress())
        .withRemoteRuntimeConnectionTimeout(
            runtimeConfiguration.getRemote().getRuntimeConnectionTimeout());
  }
}

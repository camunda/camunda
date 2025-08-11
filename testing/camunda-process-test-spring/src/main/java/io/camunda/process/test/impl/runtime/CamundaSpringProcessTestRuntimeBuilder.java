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

import io.camunda.client.CamundaClient;
import io.camunda.client.CamundaClientBuilder;
import io.camunda.client.CredentialsProvider;
import io.camunda.process.test.api.CamundaClientBuilderFactory;
import io.camunda.process.test.api.CamundaProcessTestRuntimeMode;
import io.camunda.process.test.impl.configuration.CamundaProcessTestRuntimeConfiguration;
import io.camunda.spring.client.configuration.CredentialsProviderConfiguration;
import io.camunda.spring.client.properties.CamundaClientAuthProperties;
import io.camunda.spring.client.properties.CamundaClientCloudProperties;
import io.camunda.spring.client.properties.CamundaClientProperties;
import io.camunda.spring.client.properties.CamundaClientProperties.ClientMode;

public class CamundaSpringProcessTestRuntimeBuilder {

  public static CamundaProcessTestRuntime buildRuntime(
      final CamundaProcessTestRuntimeBuilder runtimeBuilder,
      final CamundaProcessTestRuntimeConfiguration runtimeConfiguration) {

    final CamundaProcessTestRuntimeMode runtimeMode = runtimeConfiguration.getRuntimeMode();
    runtimeBuilder.withRuntimeMode(runtimeMode);

    if (runtimeMode == CamundaProcessTestRuntimeMode.MANAGED || runtimeMode == null) {
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
        .withCamundaLogger(runtimeConfiguration.getCamundaLoggerName());

    runtimeConfiguration.getCamundaExposedPorts().forEach(runtimeBuilder::withCamundaExposedPort);

    runtimeBuilder
        .withConnectorsEnabled(runtimeConfiguration.isConnectorsEnabled())
        .withConnectorsDockerImageName(runtimeConfiguration.getConnectorsDockerImageName())
        .withConnectorsDockerImageVersion(runtimeConfiguration.getConnectorsDockerImageVersion())
        .withConnectorsEnv(runtimeConfiguration.getConnectorsEnvVars())
        .withConnectorsSecrets(runtimeConfiguration.getConnectorsSecrets())
        .withConnectorsLogger(runtimeConfiguration.getCamundaLoggerName());

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
            runtimeConfiguration.getRemote().getConnectorsRestApiAddress());

    final CamundaClientBuilderFactory remoteClientBuilderFactory =
        createRemoteClientBuilderFactory(runtimeConfiguration);
    runtimeBuilder.withRemoteCamundaClientBuilderFactory(remoteClientBuilderFactory);
  }

  private static CamundaClientBuilderFactory createRemoteClientBuilderFactory(
      final CamundaProcessTestRuntimeConfiguration runtimeConfiguration) {
    final CamundaClientProperties remoteClientProperties =
        runtimeConfiguration.getRemote().getClient();

    final CamundaClientBuilder clientBuilder = createCamundaClientBuilder(remoteClientProperties);

    if (remoteClientProperties.getRestAddress() != null) {
      clientBuilder.restAddress(remoteClientProperties.getRestAddress());
    }
    if (remoteClientProperties.getGrpcAddress() != null) {
      clientBuilder.grpcAddress(remoteClientProperties.getGrpcAddress());
    }

    return () -> clientBuilder;
  }

  private static CamundaClientBuilder createCamundaClientBuilder(
      final CamundaClientProperties clientProperties) {

    if (clientProperties.getMode() == ClientMode.saas) {
      return createCamundaSaasClientBuilder(clientProperties);

    } else {
      return CamundaClient.newClientBuilder().usePlaintext();
    }
  }

  private static CamundaClientBuilder createCamundaSaasClientBuilder(
      final CamundaClientProperties clientProperties) {
    final CamundaClientCloudProperties cloudProperties = clientProperties.getCloud();
    final CamundaClientAuthProperties authProperties = clientProperties.getAuth();

    final var cloudBuilderStep =
        CamundaClient.newCloudClientBuilder()
            .withClusterId(cloudProperties.getClusterId())
            .withClientId(authProperties.getClientId())
            .withClientSecret(authProperties.getClientSecret())
            .withRegion(cloudProperties.getRegion());

    if (authProperties.getMethod() != null) {
      final CredentialsProvider credentialsProvider = createCredentialsProvider(clientProperties);
      cloudBuilderStep.credentialsProvider(credentialsProvider);
    }

    return cloudBuilderStep;
  }

  private static CredentialsProvider createCredentialsProvider(
      final CamundaClientProperties clientProperties) {
    return new CredentialsProviderConfiguration()
        .camundaClientCredentialsProvider(clientProperties);
  }
}

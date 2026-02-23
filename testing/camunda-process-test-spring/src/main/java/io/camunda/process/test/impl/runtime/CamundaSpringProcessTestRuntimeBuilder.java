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
import io.camunda.client.spring.configuration.CredentialsProviderConfiguration;
import io.camunda.client.spring.properties.CamundaClientAuthProperties;
import io.camunda.client.spring.properties.CamundaClientCloudProperties;
import io.camunda.client.spring.properties.CamundaClientJobWorkerProperties;
import io.camunda.client.spring.properties.CamundaClientProperties;
import io.camunda.client.spring.properties.CamundaClientProperties.ClientMode;
import io.camunda.process.test.api.CamundaProcessTestRuntimeMode;
import io.camunda.process.test.impl.configuration.CamundaProcessTestRuntimeConfiguration;
import java.util.function.Consumer;
import java.util.function.Function;

public class CamundaSpringProcessTestRuntimeBuilder {

  public static CamundaProcessTestRuntime buildRuntime(
      final CamundaProcessTestRuntimeBuilder runtimeBuilder,
      final CamundaProcessTestRuntimeConfiguration runtimeConfiguration,
      final CamundaClientProperties clientProperties) {

    final CamundaProcessTestRuntimeMode runtimeMode = runtimeConfiguration.getRuntimeMode();
    runtimeBuilder.withRuntimeMode(runtimeMode);

    if (runtimeMode == CamundaProcessTestRuntimeMode.MANAGED || runtimeMode == null) {
      configureManagedRuntime(runtimeBuilder, runtimeConfiguration, clientProperties);

    } else if (runtimeMode == CamundaProcessTestRuntimeMode.REMOTE) {
      configureRemoteRuntime(runtimeBuilder, runtimeConfiguration);
    }

    return runtimeBuilder.build();
  }

  private static void configureManagedRuntime(
      final CamundaProcessTestRuntimeBuilder runtimeBuilder,
      final CamundaProcessTestRuntimeConfiguration runtimeConfiguration,
      final CamundaClientProperties clientProperties) {

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

    runtimeBuilder.withCamundaClientBuilderFactory(
        () -> configureCamundaClientBuilder(clientProperties, CamundaClient.newClientBuilder()));
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

    final CamundaClientProperties clientProperties = runtimeConfiguration.getRemote().getClient();

    runtimeBuilder.withCamundaClientBuilderFactory(
        () ->
            configureCamundaClientBuilder(
                clientProperties, createCamundaClientBuilder(clientProperties)));
  }

  private static CamundaClientBuilder configureCamundaClientBuilder(
      final CamundaClientProperties clientProperties, final CamundaClientBuilder clientBuilder) {

    clientBuilder.preferRestOverGrpc(clientProperties.getPreferRestOverGrpc());

    setIfExists(clientProperties.getRestAddress(), clientBuilder::restAddress);
    setIfExists(clientProperties.getGrpcAddress(), clientBuilder::grpcAddress);
    setIfExists(clientProperties.getRequestTimeout(), clientBuilder::defaultRequestTimeout);
    setIfExists(
        clientProperties.getRequestTimeoutOffset(), clientBuilder::defaultRequestTimeoutOffset);
    setIfExists(clientProperties.getTenantId(), clientBuilder::defaultTenantId);
    setIfExists(clientProperties.getMessageTimeToLive(), clientBuilder::defaultMessageTimeToLive);
    setIfExists(
        clientProperties.getMaxMessageSize(),
        clientBuilder::maxMessageSize,
        (maxMessageSize) -> (int) maxMessageSize.toBytes());
    setIfExists(
        clientProperties.getMaxMetadataSize(),
        clientBuilder::maxMetadataSize,
        (maxMetadataSize) -> (int) maxMetadataSize.toBytes());
    setIfExists(
        clientProperties.getExecutionThreads(), clientBuilder::numJobWorkerExecutionThreads);
    setIfExists(clientProperties.getCaCertificatePath(), clientBuilder::caCertificatePath);
    setIfExists(clientProperties.getKeepAlive(), clientBuilder::keepAlive);
    setIfExists(clientProperties.getOverrideAuthority(), clientBuilder::overrideAuthority);

    final CamundaClientJobWorkerProperties jobWorkerProps =
        clientProperties.getWorker().getDefaults();

    setIfExists(jobWorkerProps.getPollInterval(), clientBuilder::defaultJobPollInterval);
    setIfExists(jobWorkerProps.getTimeout(), clientBuilder::defaultJobTimeout);
    setIfExists(jobWorkerProps.getMaxJobsActive(), clientBuilder::defaultJobWorkerMaxJobsActive);
    setIfExists(jobWorkerProps.getName(), clientBuilder::defaultJobWorkerName);
    setIfExists(jobWorkerProps.getTenantIds(), clientBuilder::defaultJobWorkerTenantIds);
    setIfExists(jobWorkerProps.getStreamEnabled(), clientBuilder::defaultJobWorkerStreamEnabled);

    return clientBuilder;
  }

  private static <T> void setIfExists(final T property, final Consumer<T> setter) {
    setIfExists(property, setter, Function.identity());
  }

  private static <T, U> void setIfExists(
      final T property, final Consumer<U> setter, final Function<T, U> transformer) {

    if (property != null) {
      setter.accept(transformer.apply(property));
    }
  }

  private static CamundaClientBuilder createCamundaClientBuilder(
      final CamundaClientProperties clientProperties) {

    if (clientProperties.getMode() == ClientMode.saas) {
      return createCamundaSaasClientBuilder(clientProperties);

    } else {
      return CamundaClient.newClientBuilder();
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

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
package io.zeebe.client.impl;

import static io.zeebe.client.impl.BuilderUtils.appendProperty;
import static io.zeebe.client.impl.command.ArgumentUtil.ensureNotNull;

import io.grpc.ClientInterceptor;
import io.zeebe.client.ClientProperties;
import io.zeebe.client.CredentialsProvider;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.ZeebeClientBuilder;
import io.zeebe.client.ZeebeClientCloudBuilderStep1;
import io.zeebe.client.ZeebeClientCloudBuilderStep1.ZeebeClientCloudBuilderStep2;
import io.zeebe.client.ZeebeClientCloudBuilderStep1.ZeebeClientCloudBuilderStep2.ZeebeClientCloudBuilderStep3;
import io.zeebe.client.ZeebeClientCloudBuilderStep1.ZeebeClientCloudBuilderStep2.ZeebeClientCloudBuilderStep3.ZeebeClientCloudBuilderStep4;
import io.zeebe.client.api.JsonMapper;
import io.zeebe.client.impl.oauth.OAuthCredentialsProviderBuilder;
import java.time.Duration;
import java.util.Objects;
import java.util.Properties;

public class ZeebeClientCloudBuilderImpl
    implements ZeebeClientCloudBuilderStep1,
        ZeebeClientCloudBuilderStep2,
        ZeebeClientCloudBuilderStep3,
        ZeebeClientCloudBuilderStep4 {

  private static final String BASE_ADDRESS = "zeebe.camunda.io";
  private static final String BASE_AUTH_URL = "https://login.cloud.camunda.io/oauth/token";

  private final ZeebeClientBuilderImpl innerBuilder = new ZeebeClientBuilderImpl();

  private String clusterId;
  private String clientId;
  private String clientSecret;

  @Override
  public ZeebeClientCloudBuilderStep2 withClusterId(final String clusterId) {
    this.clusterId = clusterId;
    return this;
  }

  @Override
  public ZeebeClientCloudBuilderStep3 withClientId(final String clientId) {
    this.clientId = clientId;
    return this;
  }

  @Override
  public ZeebeClientCloudBuilderStep4 withClientSecret(final String clientSecret) {
    this.clientSecret = clientSecret;
    return this;
  }

  @Override
  public ZeebeClientBuilder gatewayAddress(final String gatewayAddress) {
    return innerBuilder.gatewayAddress(gatewayAddress);
  }

  @Override
  public ZeebeClientBuilder usePlaintext() {
    return innerBuilder.usePlaintext();
  }

  @Override
  public ZeebeClientBuilder credentialsProvider(final CredentialsProvider credentialsProvider) {
    return innerBuilder.credentialsProvider(credentialsProvider);
  }

  @Override
  public ZeebeClientBuilder withProperties(final Properties properties) {
    if (properties.containsKey(ClientProperties.CLOUD_CLUSTER_ID)) {
      withClusterId(properties.getProperty(ClientProperties.CLOUD_CLUSTER_ID));
    }
    if (properties.containsKey(ClientProperties.CLOUD_CLIENT_ID)) {
      withClientId(properties.getProperty(ClientProperties.CLOUD_CLIENT_ID));
    }
    if (properties.containsKey(ClientProperties.CLOUD_CLIENT_SECRET)) {
      withClientSecret(properties.getProperty(ClientProperties.CLOUD_CLIENT_SECRET));
    }
    return innerBuilder.withProperties(properties);
  }

  @Override
  public ZeebeClientBuilder defaultJobWorkerMaxJobsActive(final int maxJobsActive) {
    return innerBuilder.defaultJobWorkerMaxJobsActive(maxJobsActive);
  }

  @Override
  public ZeebeClientBuilder numJobWorkerExecutionThreads(final int numThreads) {
    return innerBuilder.numJobWorkerExecutionThreads(numThreads);
  }

  @Override
  public ZeebeClientBuilder defaultJobWorkerName(final String workerName) {
    return innerBuilder.defaultJobWorkerName(workerName);
  }

  @Override
  public ZeebeClientBuilder defaultJobTimeout(final Duration timeout) {
    return innerBuilder.defaultJobTimeout(timeout);
  }

  @Override
  public ZeebeClientBuilder defaultJobPollInterval(final Duration pollInterval) {
    return innerBuilder.defaultJobPollInterval(pollInterval);
  }

  @Override
  public ZeebeClientBuilder defaultMessageTimeToLive(final Duration timeToLive) {
    return innerBuilder.defaultMessageTimeToLive(timeToLive);
  }

  @Override
  public ZeebeClientBuilder defaultRequestTimeout(final Duration requestTimeout) {
    return innerBuilder.defaultRequestTimeout(requestTimeout);
  }

  @Override
  public ZeebeClientBuilder caCertificatePath(final String certificatePath) {
    return innerBuilder.caCertificatePath(certificatePath);
  }

  @Override
  public ZeebeClientBuilder keepAlive(final Duration keepAlive) {
    return innerBuilder.keepAlive(keepAlive);
  }

  @Override
  public ZeebeClientBuilder withInterceptors(final ClientInterceptor... interceptor) {
    return innerBuilder.withInterceptors(interceptor);
  }

  @Override
  public ZeebeClientBuilder withJsonMapper(final JsonMapper jsonMapper) {
    return innerBuilder.withJsonMapper(jsonMapper);
  }

  @Override
  public ZeebeClient build() {
    innerBuilder.gatewayAddress(determineGatewayAddress());
    innerBuilder.credentialsProvider(determineCredentialsProvider());
    return innerBuilder.build();
  }

  private String determineGatewayAddress() {
    if (isNeedToSetCloudGatewayAddress()) {
      ensureNotNull("cluster id", clusterId);
      return String.format("%s.%s:443", clusterId, BASE_ADDRESS);
    } else {
      Loggers.LOGGER.debug(
          "Expected to use 'cluster id' to set gateway address in the client cloud builder, "
              + "but overwriting with explicitly defined gateway address: {}.",
          innerBuilder.getGatewayAddress());
      return innerBuilder.getGatewayAddress();
    }
  }

  private CredentialsProvider determineCredentialsProvider() {
    final CredentialsProvider provider = innerBuilder.getCredentialsProvider();
    if (provider == null) {
      ensureNotNull("cluster id", clusterId);
      ensureNotNull("client id", clientId);
      ensureNotNull("client secret", clientSecret);
      final OAuthCredentialsProviderBuilder builder = new OAuthCredentialsProviderBuilder();
      return builder
          .audience(String.format("%s.%s", clusterId, BASE_ADDRESS))
          .clientId(clientId)
          .clientSecret(clientSecret)
          .authorizationServerUrl(BASE_AUTH_URL)
          .build();
    } else {
      Loggers.LOGGER.debug(
          "Expected to use 'cluster id', 'client id' and 'client secret' to set credentials provider in the client cloud builder, "
              + "but overwriting with explicitly defined credentials provider.");
      return provider;
    }
  }

  private boolean isNeedToSetCloudGatewayAddress() {
    return innerBuilder.getGatewayAddress() == null
        || Objects.equals(
            innerBuilder.getGatewayAddress(), ZeebeClientBuilderImpl.DEFAULT_GATEWAY_ADDRESS);
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder(innerBuilder.toString());
    appendProperty(sb, "clusterId", clusterId);
    appendProperty(sb, "clientId", clientId);
    return sb.toString();
  }
}

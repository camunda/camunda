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
package io.camunda.zeebe.client.impl;

import static io.camunda.zeebe.client.impl.BuilderUtils.appendProperty;
import static io.camunda.zeebe.client.impl.command.ArgumentUtil.ensureNotNull;

import io.camunda.zeebe.client.ClientProperties;
import io.camunda.zeebe.client.CredentialsProvider;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.ZeebeClientBuilder;
import io.camunda.zeebe.client.ZeebeClientCloudBuilderStep1;
import io.camunda.zeebe.client.ZeebeClientCloudBuilderStep1.ZeebeClientCloudBuilderStep2;
import io.camunda.zeebe.client.ZeebeClientCloudBuilderStep1.ZeebeClientCloudBuilderStep2.ZeebeClientCloudBuilderStep3;
import io.camunda.zeebe.client.ZeebeClientCloudBuilderStep1.ZeebeClientCloudBuilderStep2.ZeebeClientCloudBuilderStep3.ZeebeClientCloudBuilderStep4;
import io.camunda.zeebe.client.api.JsonMapper;
import io.camunda.zeebe.client.impl.oauth.OAuthCredentialsProviderBuilder;
import io.grpc.ClientInterceptor;
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

  private static final String DEFAULT_REGION = "bru-2";

  private final ZeebeClientBuilderImpl innerBuilder = new ZeebeClientBuilderImpl();

  private String clusterId;
  private String clientId;
  private String clientSecret;
  private String region = DEFAULT_REGION;

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
  public ZeebeClientCloudBuilderStep4 withRegion(final String region) {
    this.region = region;
    return this;
  }

  @Override
  public ZeebeClientCloudBuilderStep4 withProperties(final Properties properties) {
    if (properties.containsKey(ClientProperties.CLOUD_CLUSTER_ID)) {
      withClusterId(properties.getProperty(ClientProperties.CLOUD_CLUSTER_ID));
    }
    if (properties.containsKey(ClientProperties.CLOUD_CLIENT_ID)) {
      withClientId(properties.getProperty(ClientProperties.CLOUD_CLIENT_ID));
    }
    if (properties.containsKey(ClientProperties.CLOUD_CLIENT_SECRET)) {
      withClientSecret(properties.getProperty(ClientProperties.CLOUD_CLIENT_SECRET));
    }
    if (properties.containsKey(ClientProperties.CLOUD_REGION)) {
      withRegion(properties.getProperty(ClientProperties.CLOUD_REGION));
    }
    innerBuilder.withProperties(properties);
    return this;
  }

  @Override
  public ZeebeClientBuilder applyEnvironmentVariableOverrides(
      boolean applyEnvironmentVariableOverrides) {
    innerBuilder.applyEnvironmentVariableOverrides(applyEnvironmentVariableOverrides);
    return this;
  }

  @Override
  public ZeebeClientCloudBuilderStep4 gatewayAddress(final String gatewayAddress) {
    innerBuilder.gatewayAddress(gatewayAddress);
    return this;
  }

  @Override
  public ZeebeClientCloudBuilderStep4 defaultJobWorkerMaxJobsActive(final int maxJobsActive) {
    innerBuilder.defaultJobWorkerMaxJobsActive(maxJobsActive);
    return this;
  }

  @Override
  public ZeebeClientCloudBuilderStep4 numJobWorkerExecutionThreads(final int numThreads) {
    innerBuilder.numJobWorkerExecutionThreads(numThreads);
    return this;
  }

  @Override
  public ZeebeClientCloudBuilderStep4 defaultJobWorkerName(final String workerName) {
    innerBuilder.defaultJobWorkerName(workerName);
    return this;
  }

  @Override
  public ZeebeClientCloudBuilderStep4 defaultJobTimeout(final Duration timeout) {
    innerBuilder.defaultJobTimeout(timeout);
    return this;
  }

  @Override
  public ZeebeClientCloudBuilderStep4 defaultJobPollInterval(final Duration pollInterval) {
    innerBuilder.defaultJobPollInterval(pollInterval);
    return this;
  }

  @Override
  public ZeebeClientCloudBuilderStep4 defaultMessageTimeToLive(final Duration timeToLive) {
    innerBuilder.defaultMessageTimeToLive(timeToLive);
    return this;
  }

  @Override
  public ZeebeClientCloudBuilderStep4 defaultRequestTimeout(final Duration requestTimeout) {
    innerBuilder.defaultRequestTimeout(requestTimeout);
    return this;
  }

  @Override
  public ZeebeClientCloudBuilderStep4 usePlaintext() {
    innerBuilder.usePlaintext();
    return this;
  }

  @Override
  public ZeebeClientCloudBuilderStep4 caCertificatePath(final String certificatePath) {
    innerBuilder.caCertificatePath(certificatePath);
    return this;
  }

  @Override
  public ZeebeClientCloudBuilderStep4 credentialsProvider(
      final CredentialsProvider credentialsProvider) {
    innerBuilder.credentialsProvider(credentialsProvider);
    return this;
  }

  @Override
  public ZeebeClientCloudBuilderStep4 keepAlive(final Duration keepAlive) {
    innerBuilder.keepAlive(keepAlive);
    return this;
  }

  @Override
  public ZeebeClientCloudBuilderStep4 withInterceptors(final ClientInterceptor... interceptor) {
    innerBuilder.withInterceptors(interceptor);
    return this;
  }

  @Override
  public ZeebeClientCloudBuilderStep4 withJsonMapper(final JsonMapper jsonMapper) {
    innerBuilder.withJsonMapper(jsonMapper);
    return this;
  }

  @Override
  public ZeebeClientBuilder overrideAuthority(final String authority) {
    innerBuilder.overrideAuthority(authority);
    return this;
  }

  @Override
  public ZeebeClientBuilder maxMessageSize(final int maxMessageSize) {
    innerBuilder.maxMessageSize(maxMessageSize);
    return this;
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
      return String.format("%s.%s.%s:443", clusterId, region, BASE_ADDRESS);
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

      if (innerBuilder.isPlaintextConnectionEnabled()) {
        Loggers.LOGGER.debug("Expected setting 'usePlaintext' to be 'false', but found 'true'.");
      }
      return builder
          .audience(String.format("%s.%s.%s", clusterId, region, BASE_ADDRESS))
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
    appendProperty(sb, "region", region);
    return sb.toString();
  }
}

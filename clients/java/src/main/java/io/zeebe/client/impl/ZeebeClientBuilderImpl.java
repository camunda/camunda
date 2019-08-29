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

import static io.zeebe.client.ClientProperties.CA_CERTIFICATE_PATH;
import static io.zeebe.client.ClientProperties.DEFAULT_MESSAGE_TIME_TO_LIVE;
import static io.zeebe.client.ClientProperties.DEFAULT_REQUEST_TIMEOUT;
import static io.zeebe.client.ClientProperties.USE_PLAINTEXT_CONNECTION;

import io.zeebe.client.ClientProperties;
import io.zeebe.client.CredentialsProvider;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.ZeebeClientBuilder;
import io.zeebe.client.ZeebeClientConfiguration;
import io.zeebe.client.impl.oauth.OAuthCredentialsProviderBuilder;
import java.time.Duration;
import java.util.Properties;

public class ZeebeClientBuilderImpl implements ZeebeClientBuilder, ZeebeClientConfiguration {
  private String brokerContactPoint = "0.0.0.0:26500";
  private int jobWorkerMaxJobsActive = 32;
  private int numJobWorkerExecutionThreads = 1;
  private String defaultJobWorkerName = "default";
  private Duration defaultJobTimeout = Duration.ofMinutes(5);
  private Duration defaultJobPollInterval = Duration.ofMillis(100);
  private Duration defaultMessageTimeToLive = Duration.ofHours(1);
  private Duration defaultRequestTimeout = Duration.ofSeconds(20);
  private boolean usePlaintextConnection = false;
  private String certificatePath;
  private CredentialsProvider credentialsProvider;
  private String clientId;
  private String clientSecret;
  private String authzServerUrl;

  @Override
  public String getBrokerContactPoint() {
    return brokerContactPoint;
  }

  @Override
  public int getNumJobWorkerExecutionThreads() {
    return numJobWorkerExecutionThreads;
  }

  @Override
  public int getDefaultJobWorkerMaxJobsActive() {
    return jobWorkerMaxJobsActive;
  }

  @Override
  public String getDefaultJobWorkerName() {
    return defaultJobWorkerName;
  }

  @Override
  public Duration getDefaultJobTimeout() {
    return defaultJobTimeout;
  }

  @Override
  public Duration getDefaultJobPollInterval() {
    return defaultJobPollInterval;
  }

  @Override
  public Duration getDefaultMessageTimeToLive() {
    return defaultMessageTimeToLive;
  }

  @Override
  public Duration getDefaultRequestTimeout() {
    return defaultRequestTimeout;
  }

  @Override
  public boolean isPlaintextConnectionEnabled() {
    return usePlaintextConnection;
  }

  @Override
  public String getCaCertificatePath() {
    return certificatePath;
  }

  @Override
  public CredentialsProvider getCredentialsProvider() {
    return credentialsProvider;
  }

  @Override
  public ZeebeClientBuilder withProperties(final Properties properties) {
    if (properties.containsKey(ClientProperties.BROKER_CONTACTPOINT)) {
      brokerContactPoint(properties.getProperty(ClientProperties.BROKER_CONTACTPOINT));
    }

    if (properties.containsKey(ClientProperties.JOB_WORKER_EXECUTION_THREADS)) {
      numJobWorkerExecutionThreads(
          Integer.parseInt(properties.getProperty(ClientProperties.JOB_WORKER_EXECUTION_THREADS)));
    }
    if (properties.containsKey(ClientProperties.JOB_WORKER_MAX_JOBS_ACTIVE)) {
      defaultJobWorkerMaxJobsActive(
          Integer.parseInt(properties.getProperty(ClientProperties.JOB_WORKER_MAX_JOBS_ACTIVE)));
    }
    if (properties.containsKey(ClientProperties.DEFAULT_JOB_WORKER_NAME)) {
      defaultJobWorkerName(properties.getProperty(ClientProperties.DEFAULT_JOB_WORKER_NAME));
    }
    if (properties.containsKey(ClientProperties.DEFAULT_JOB_TIMEOUT)) {
      defaultJobTimeout(
          Duration.ofMillis(
              Integer.parseInt(properties.getProperty(ClientProperties.DEFAULT_JOB_TIMEOUT))));
    }
    if (properties.containsKey(ClientProperties.DEFAULT_JOB_POLL_INTERVAL)) {
      defaultJobTimeout(
          Duration.ofMillis(
              Integer.parseInt(
                  properties.getProperty(ClientProperties.DEFAULT_JOB_POLL_INTERVAL))));
    }
    if (properties.containsKey(DEFAULT_MESSAGE_TIME_TO_LIVE)) {
      defaultMessageTimeToLive(
          Duration.ofMillis(Long.parseLong(properties.getProperty(DEFAULT_MESSAGE_TIME_TO_LIVE))));
    }
    if (properties.containsKey(DEFAULT_REQUEST_TIMEOUT)) {
      defaultRequestTimeout(
          Duration.ofMillis(Long.parseLong(properties.getProperty(DEFAULT_REQUEST_TIMEOUT))));
    }
    if (properties.containsKey(USE_PLAINTEXT_CONNECTION)) {
      usePlaintext();
    }
    if (properties.containsKey(CA_CERTIFICATE_PATH)) {
      caCertificatePath(properties.getProperty(CA_CERTIFICATE_PATH));
    }

    return this;
  }

  @Override
  public ZeebeClientBuilder brokerContactPoint(final String contactPoint) {
    this.brokerContactPoint = contactPoint;
    return this;
  }

  @Override
  public ZeebeClientBuilder defaultJobWorkerMaxJobsActive(final int maxJobsActive) {
    this.jobWorkerMaxJobsActive = maxJobsActive;
    return this;
  }

  @Override
  public ZeebeClientBuilder numJobWorkerExecutionThreads(final int numSubscriptionThreads) {
    this.numJobWorkerExecutionThreads = numSubscriptionThreads;
    return this;
  }

  @Override
  public ZeebeClientBuilder defaultJobWorkerName(final String workerName) {
    this.defaultJobWorkerName = workerName;
    return this;
  }

  @Override
  public ZeebeClientBuilder defaultJobTimeout(final Duration timeout) {
    this.defaultJobTimeout = timeout;
    return this;
  }

  @Override
  public ZeebeClientBuilder defaultJobPollInterval(final Duration pollInterval) {
    defaultJobPollInterval = pollInterval;
    return this;
  }

  @Override
  public ZeebeClientBuilder defaultMessageTimeToLive(final Duration timeToLive) {
    this.defaultMessageTimeToLive = timeToLive;
    return this;
  }

  @Override
  public ZeebeClientBuilder defaultRequestTimeout(final Duration requestTimeout) {
    this.defaultRequestTimeout = requestTimeout;
    return this;
  }

  @Override
  public ZeebeClientBuilder usePlaintext() {
    this.usePlaintextConnection = true;
    return this;
  }

  @Override
  public ZeebeClientBuilder caCertificatePath(final String certificatePath) {
    this.certificatePath = certificatePath;
    return this;
  }

  @Override
  public ZeebeClientBuilder credentialsProvider(final CredentialsProvider credentialsProvider) {
    this.credentialsProvider = credentialsProvider;
    return this;
  }

  @Override
  public ZeebeClientBuilder oAuthCredentialsProvider(
      final String clientId, final String clientSecret) {
    this.clientId = clientId;
    this.clientSecret = clientSecret;
    return this;
  }

  @Override
  public ZeebeClientBuilder oAuthCredentialsProvider(
      final String clientId, final String clientSecret, final String authzServerUrl) {
    this.authzServerUrl = authzServerUrl;
    return oAuthCredentialsProvider(clientId, clientSecret);
  }

  @Override
  public ZeebeClient build() {
    applyDefaults();

    return new ZeebeClientImpl(this);
  }

  private void applyDefaults() {
    if (clientId != null && clientSecret != null) {
      final int separatorIndex = brokerContactPoint.lastIndexOf(':');
      if (separatorIndex > 0) {
        final String audience = brokerContactPoint.substring(0, separatorIndex);

        this.credentialsProvider =
            new OAuthCredentialsProviderBuilder()
                .clientId(clientId)
                .clientSecret(clientSecret)
                .audience(audience)
                .authorizationServerUrl(authzServerUrl)
                .build();
      }
    }
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder();

    appendProperty(sb, "brokerContactPoint", brokerContactPoint);
    appendProperty(sb, "jobWorkerMaxJobsActive", jobWorkerMaxJobsActive);
    appendProperty(sb, "numJobWorkerExecutionThreads", numJobWorkerExecutionThreads);
    appendProperty(sb, "defaultJobWorkerName", defaultJobWorkerName);
    appendProperty(sb, "defaultJobTimeout", defaultJobTimeout);
    appendProperty(sb, "defaultJobPollInterval", defaultJobPollInterval);
    appendProperty(sb, "defaultMessageTimeToLive", defaultMessageTimeToLive);
    appendProperty(sb, "defaultRequestTimeout", defaultRequestTimeout);

    return sb.toString();
  }

  private static void appendProperty(
      final StringBuilder sb, final String propertyName, final Object value) {
    sb.append(propertyName + ": " + value + "\n");
  }
}

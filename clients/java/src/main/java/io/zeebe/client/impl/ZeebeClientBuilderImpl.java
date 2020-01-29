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
import static io.zeebe.client.ClientProperties.KEEP_ALIVE;
import static io.zeebe.client.ClientProperties.USE_PLAINTEXT_CONNECTION;

import io.grpc.ClientInterceptor;
import io.zeebe.client.ClientProperties;
import io.zeebe.client.CredentialsProvider;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.ZeebeClientBuilder;
import io.zeebe.client.ZeebeClientConfiguration;
import io.zeebe.client.impl.oauth.OAuthCredentialsProviderBuilder;
import io.zeebe.client.util.Environment;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

public final class ZeebeClientBuilderImpl implements ZeebeClientBuilder, ZeebeClientConfiguration {
  public static final String PLAINTEXT_CONNECTION_VAR = "ZEEBE_INSECURE_CONNECTION";
  public static final String CA_CERTIFICATE_VAR = "ZEEBE_CA_CERTIFICATE_PATH";
  public static final String KEEP_ALIVE_VAR = "ZEEBE_KEEP_ALIVE";
  private static final String ILLEGAL_KEEP_ALIVE_FMT =
      "Keep alive must be expressed as a positive integer followed by either s (seconds), m (minutes) or h (hours) but got instead '%s' instead.";
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
  private Duration keepAlive = Duration.ofSeconds(45);
  private final List<ClientInterceptor> interceptors = new ArrayList<>();

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
  public Duration getKeepAlive() {
    return keepAlive;
  }

  @Override
  public List getInterceptors() {
    return interceptors;
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
    if (properties.containsKey(KEEP_ALIVE)) {
      keepAlive(properties.getProperty(KEEP_ALIVE));
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
  public ZeebeClientBuilder keepAlive(final Duration keepAlive) {
    if (keepAlive.isNegative() || keepAlive.isZero()) {
      throw new IllegalArgumentException("The keep alive must be a positive number.");
    }

    this.keepAlive = keepAlive;
    return this;
  }

  @Override
  public ZeebeClientBuilder withInterceptors(final ClientInterceptor... interceptors) {
    this.interceptors.addAll(Arrays.asList(interceptors));
    return this;
  }

  @Override
  public ZeebeClient build() {
    applyOverrides();
    applyDefaults();

    return new ZeebeClientImpl(this);
  }

  private void keepAlive(final String keepAlive) {
    keepAlive(Duration.ofMillis(Long.parseUnsignedLong(keepAlive)));
  }

  private void applyOverrides() {
    if (Environment.system().isDefined(PLAINTEXT_CONNECTION_VAR)) {
      usePlaintextConnection = Environment.system().getBoolean(PLAINTEXT_CONNECTION_VAR);
    }

    if (Environment.system().isDefined(CA_CERTIFICATE_VAR)) {
      caCertificatePath(Environment.system().get(CA_CERTIFICATE_VAR));
    }

    if (Environment.system().isDefined(KEEP_ALIVE_VAR)) {
      keepAlive(Environment.system().get(KEEP_ALIVE_VAR));
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

  private void applyDefaults() {
    if (shouldUseDefaultCredentialsProvider()) {
      credentialsProvider = createDefaultCredentialsProvider();
    }
  }

  private boolean shouldUseDefaultCredentialsProvider() {
    return credentialsProvider == null
        && Environment.system().get(OAuthCredentialsProviderBuilder.OAUTH_ENV_CLIENT_ID) != null
        && Environment.system().get(OAuthCredentialsProviderBuilder.OAUTH_ENV_CLIENT_SECRET)
            != null;
  }

  private CredentialsProvider createDefaultCredentialsProvider() {
    final OAuthCredentialsProviderBuilder builder =
        CredentialsProvider.newCredentialsProviderBuilder();
    final int separatorIndex = brokerContactPoint.lastIndexOf(':');
    if (separatorIndex > 0) {
      builder.audience(brokerContactPoint.substring(0, separatorIndex));
    }

    return builder.build();
  }

  private static void appendProperty(
      final StringBuilder sb, final String propertyName, final Object value) {
    sb.append(propertyName + ": " + value + "\n");
  }
}

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

import static io.camunda.zeebe.client.ClientProperties.CA_CERTIFICATE_PATH;
import static io.camunda.zeebe.client.ClientProperties.DEFAULT_MESSAGE_TIME_TO_LIVE;
import static io.camunda.zeebe.client.ClientProperties.DEFAULT_REQUEST_TIMEOUT;
import static io.camunda.zeebe.client.ClientProperties.KEEP_ALIVE;
import static io.camunda.zeebe.client.ClientProperties.MAX_MESSAGE_SIZE;
import static io.camunda.zeebe.client.ClientProperties.MAX_METADATA_SIZE;
import static io.camunda.zeebe.client.ClientProperties.OVERRIDE_AUTHORITY;
import static io.camunda.zeebe.client.ClientProperties.PREFER_REST_OVER_GRPC;
import static io.camunda.zeebe.client.ClientProperties.STREAM_ENABLED;
import static io.camunda.zeebe.client.ClientProperties.USE_DEFAULT_RETRY_POLICY;
import static io.camunda.zeebe.client.ClientProperties.USE_PLAINTEXT_CONNECTION;
import static io.camunda.zeebe.client.impl.BuilderUtils.appendProperty;
import static io.camunda.zeebe.client.impl.util.DataSizeUtil.ONE_KB;
import static io.camunda.zeebe.client.impl.util.DataSizeUtil.ONE_MB;

import io.camunda.zeebe.client.ClientProperties;
import io.camunda.zeebe.client.CredentialsProvider;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.ZeebeClientBuilder;
import io.camunda.zeebe.client.ZeebeClientConfiguration;
import io.camunda.zeebe.client.api.JsonMapper;
import io.camunda.zeebe.client.api.command.CommandWithTenantStep;
import io.camunda.zeebe.client.impl.oauth.OAuthCredentialsProviderBuilder;
import io.camunda.zeebe.client.impl.util.DataSizeUtil;
import io.camunda.zeebe.client.impl.util.Environment;
import io.grpc.ClientInterceptor;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ScheduledExecutorService;

public final class ZeebeClientBuilderImpl implements ZeebeClientBuilder, ZeebeClientConfiguration {

  public static final String PLAINTEXT_CONNECTION_VAR = "ZEEBE_INSECURE_CONNECTION";
  public static final String CA_CERTIFICATE_VAR = "ZEEBE_CA_CERTIFICATE_PATH";
  public static final String KEEP_ALIVE_VAR = "ZEEBE_KEEP_ALIVE";
  public static final String OVERRIDE_AUTHORITY_VAR = "ZEEBE_OVERRIDE_AUTHORITY";
  public static final String ZEEBE_CLIENT_WORKER_STREAM_ENABLED =
      "ZEEBE_CLIENT_WORKER_STREAM_ENABLED";
  public static final String DEFAULT_GATEWAY_ADDRESS = "0.0.0.0:26500";
  public static final URI DEFAULT_GRPC_ADDRESS =
      getURIFromString("https://" + DEFAULT_GATEWAY_ADDRESS);
  public static final URI DEFAULT_REST_ADDRESS = getURIFromString("https://0.0.0.0:8080");
  public static final String REST_ADDRESS_VAR = "ZEEBE_REST_ADDRESS";
  public static final String GRPC_ADDRESS_VAR = "ZEEBE_GRPC_ADDRESS";
  public static final String PREFER_REST_VAR = "ZEEBE_PREFER_REST";
  public static final String DEFAULT_TENANT_ID_VAR = "ZEEBE_DEFAULT_TENANT_ID";
  public static final String DEFAULT_JOB_WORKER_TENANT_IDS_VAR =
      "ZEEBE_DEFAULT_JOB_WORKER_TENANT_IDS";
  public static final String DEFAULT_JOB_WORKER_NAME = "default";
  public static final String USE_DEFAULT_RETRY_POLICY_VAR = "ZEEBE_CLIENT_USE_DEFAULT_RETRY_POLICY";
  private static final String TENANT_ID_LIST_SEPARATOR = ",";
  private static final boolean DEFAULT_PREFER_REST_OVER_GRPC = false;

  private boolean applyEnvironmentVariableOverrides = true;

  private final List<ClientInterceptor> interceptors = new ArrayList<>();
  private String gatewayAddress = DEFAULT_GATEWAY_ADDRESS;
  private URI restAddress = DEFAULT_REST_ADDRESS;
  private URI grpcAddress = DEFAULT_GRPC_ADDRESS;
  private boolean preferRestOverGrpc = DEFAULT_PREFER_REST_OVER_GRPC;
  private String defaultTenantId = CommandWithTenantStep.DEFAULT_TENANT_IDENTIFIER;
  private List<String> defaultJobWorkerTenantIds =
      Collections.singletonList(CommandWithTenantStep.DEFAULT_TENANT_IDENTIFIER);
  private int jobWorkerMaxJobsActive = 32;
  private int numJobWorkerExecutionThreads = 1;
  private String defaultJobWorkerName = DEFAULT_JOB_WORKER_NAME;
  private Duration defaultJobTimeout = Duration.ofMinutes(5);
  private Duration defaultJobPollInterval = Duration.ofMillis(100);
  private Duration defaultMessageTimeToLive = Duration.ofHours(1);
  private Duration defaultRequestTimeout = Duration.ofSeconds(10);
  private boolean usePlaintextConnection = false;
  private String certificatePath;
  private CredentialsProvider credentialsProvider;
  private Duration keepAlive = Duration.ofSeconds(45);
  private JsonMapper jsonMapper = new ZeebeObjectMapper();
  private String overrideAuthority;
  private int maxMessageSize = 5 * ONE_MB;
  private int maxMetadataSize = 16 * ONE_KB;
  private boolean streamEnabled = false;
  private boolean grpcAddressUsed = false;
  private ScheduledExecutorService jobWorkerExecutor;
  private boolean ownsJobWorkerExecutor;
  private boolean useDefaultRetryPolicy;

  @Override
  public String getGatewayAddress() {
    return gatewayAddress;
  }

  @Override
  public URI getRestAddress() {
    return restAddress;
  }

  @Override
  public URI getGrpcAddress() {
    return grpcAddress;
  }

  @Override
  public String getDefaultTenantId() {
    return defaultTenantId;
  }

  @Override
  public List<String> getDefaultJobWorkerTenantIds() {
    return defaultJobWorkerTenantIds;
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
  public List<ClientInterceptor> getInterceptors() {
    return interceptors;
  }

  @Override
  public JsonMapper getJsonMapper() {
    return jsonMapper;
  }

  @Override
  public String getOverrideAuthority() {
    return overrideAuthority;
  }

  @Override
  public int getMaxMessageSize() {
    return maxMessageSize;
  }

  @Override
  public int getMaxMetadataSize() {
    return maxMetadataSize;
  }

  @Override
  public ScheduledExecutorService jobWorkerExecutor() {
    return jobWorkerExecutor;
  }

  @Override
  public boolean ownsJobWorkerExecutor() {
    return ownsJobWorkerExecutor;
  }

  @Override
  public boolean getDefaultJobWorkerStreamEnabled() {
    return streamEnabled;
  }

  @Override
  public boolean useDefaultRetryPolicy() {
    return useDefaultRetryPolicy;
  }

  @Override
  public boolean preferRestOverGrpc() {
    return preferRestOverGrpc;
  }

  @Override
  public ZeebeClientBuilder withProperties(final Properties properties) {
    if (properties.containsKey(ClientProperties.APPLY_ENVIRONMENT_VARIABLES_OVERRIDES)) {
      applyEnvironmentVariableOverrides(
          Boolean.parseBoolean(
              properties.getProperty(ClientProperties.APPLY_ENVIRONMENT_VARIABLES_OVERRIDES)));
    }
    if (properties.containsKey(ClientProperties.GRPC_ADDRESS)) {
      final URI grpcAddr = getURIFromString(properties.getProperty(ClientProperties.GRPC_ADDRESS));
      grpcAddress(grpcAddr);
    }
    if (properties.containsKey(ClientProperties.REST_ADDRESS)) {
      final URI restAddr = getURIFromString(properties.getProperty(ClientProperties.REST_ADDRESS));
      restAddress(restAddr);
    }
    if (properties.containsKey(ClientProperties.GATEWAY_ADDRESS)) {
      gatewayAddress(properties.getProperty(ClientProperties.GATEWAY_ADDRESS));
    }
    if (properties.containsKey(PREFER_REST_OVER_GRPC)) {
      preferRestOverGrpc(Boolean.parseBoolean(properties.getProperty(PREFER_REST_OVER_GRPC)));
    }
    if (properties.containsKey(ClientProperties.DEFAULT_TENANT_ID)) {
      defaultTenantId(properties.getProperty(ClientProperties.DEFAULT_TENANT_ID));
    }
    if (properties.containsKey(ClientProperties.DEFAULT_JOB_WORKER_TENANT_IDS)) {
      final String tenantIdsList =
          properties.getProperty(ClientProperties.DEFAULT_JOB_WORKER_TENANT_IDS);
      final List<String> tenantIds = Arrays.asList(tenantIdsList.split(TENANT_ID_LIST_SEPARATOR));
      defaultJobWorkerTenantIds(tenantIds);
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
      defaultJobPollInterval(
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
      /**
       * The following condition is phrased in this particular way in order to be backwards
       * compatible with older versions of the software. In older versions the content of the
       * property was not interpreted. It was assumed to be true, whenever it was set. Because of
       * that, code examples in this code base set the flag to an empty string. By phrasing the
       * condition this way, the old code will still work with this new implementation. Only if
       * somebody deliberately sets the flag to false, the behavior will change
       */
      if (!"false".equalsIgnoreCase(properties.getProperty(USE_PLAINTEXT_CONNECTION))) {
        usePlaintext();
      }
    }
    if (properties.containsKey(CA_CERTIFICATE_PATH)) {
      caCertificatePath(properties.getProperty(CA_CERTIFICATE_PATH));
    }
    if (properties.containsKey(KEEP_ALIVE)) {
      keepAlive(properties.getProperty(KEEP_ALIVE));
    }
    if (properties.containsKey(OVERRIDE_AUTHORITY)) {
      overrideAuthority(properties.getProperty(OVERRIDE_AUTHORITY));
    }
    if (properties.containsKey(MAX_MESSAGE_SIZE)) {
      maxMessageSize(DataSizeUtil.parse(properties.getProperty(MAX_MESSAGE_SIZE)));
    }
    if (properties.containsKey(MAX_METADATA_SIZE)) {
      maxMetadataSize(DataSizeUtil.parse(properties.getProperty(MAX_METADATA_SIZE)));
    }
    if (properties.containsKey(STREAM_ENABLED)) {
      defaultJobWorkerStreamEnabled(Boolean.parseBoolean(properties.getProperty(STREAM_ENABLED)));
    }
    if (properties.containsKey(USE_DEFAULT_RETRY_POLICY)) {
      useDefaultRetryPolicy(Boolean.parseBoolean(properties.getProperty(USE_DEFAULT_RETRY_POLICY)));
    }
    return this;
  }

  @Override
  public ZeebeClientBuilder applyEnvironmentVariableOverrides(
      final boolean applyEnvironmentVariableOverrides) {
    this.applyEnvironmentVariableOverrides = applyEnvironmentVariableOverrides;
    return this;
  }

  @Override
  public ZeebeClientBuilder gatewayAddress(final String gatewayAddress) {
    this.gatewayAddress = gatewayAddress;
    return this;
  }

  @Override
  public ZeebeClientBuilder restAddress(final URI restAddress) {
    /*
     * Validates that the provided rest address is an absolute URI.
     *
     * <p>We use {@code URI.getHost() == null} to check for absolute URIs because:
     * <ul>
     *   <li>For absolute URIs (with a scheme) (e.g., "https://example.com"), {@code URI.getHost()} returns the hostname (e.g., "example.com").</li>
     *   <li>For relative URIs (without a scheme) (e.g., "example.com"), {@code URI.getHost()} returns {@code null}.</li>
     * </ul>
     */
    if (restAddress != null && restAddress.getHost() == null) {
      throw new IllegalArgumentException("restAddress must be an absolute URI");
    }
    this.restAddress = restAddress;
    return this;
  }

  @Override
  public ZeebeClientBuilder grpcAddress(final URI grpcAddress) {
    /*
     * Validates that the provided gRPC address is an absolute URI.
     *
     * <p>We use {@code URI.getHost() == null} to check for absolute URIs because:
     * <ul>
     *   <li>For absolute URIs (with a scheme) (e.g., "https://example.com"), {@code URI.getHost()} returns the hostname (e.g., "example.com").</li>
     *   <li>For relative URIs (without a scheme) (e.g., "example.com"), {@code URI.getHost()} returns {@code null}.</li>
     * </ul>
     */
    if (grpcAddress != null && grpcAddress.getHost() == null) {
      throw new IllegalArgumentException("grpcAddress must be an absolute URI");
    }
    this.grpcAddress = grpcAddress;
    grpcAddressUsed = true;
    return this;
  }

  @Override
  public ZeebeClientBuilder defaultTenantId(final String tenantId) {
    defaultTenantId = tenantId;
    return this;
  }

  @Override
  public ZeebeClientBuilder defaultJobWorkerTenantIds(final List<String> tenantIds) {
    defaultJobWorkerTenantIds = tenantIds;
    return this;
  }

  @Override
  public ZeebeClientBuilder defaultJobWorkerMaxJobsActive(final int maxJobsActive) {
    jobWorkerMaxJobsActive = maxJobsActive;
    return this;
  }

  @Override
  public ZeebeClientBuilder numJobWorkerExecutionThreads(final int numSubscriptionThreads) {
    numJobWorkerExecutionThreads = numSubscriptionThreads;
    return this;
  }

  @Override
  public ZeebeClientBuilder jobWorkerExecutor(
      final ScheduledExecutorService executor, final boolean takeOwnership) {
    jobWorkerExecutor = executor;
    ownsJobWorkerExecutor = takeOwnership;
    return this;
  }

  @Override
  public ZeebeClientBuilder defaultJobWorkerName(final String workerName) {
    if (workerName != null) {
      defaultJobWorkerName = workerName;
    }
    return this;
  }

  @Override
  public ZeebeClientBuilder defaultJobTimeout(final Duration timeout) {
    defaultJobTimeout = timeout;
    return this;
  }

  @Override
  public ZeebeClientBuilder defaultJobPollInterval(final Duration pollInterval) {
    defaultJobPollInterval = pollInterval;
    return this;
  }

  @Override
  public ZeebeClientBuilder defaultMessageTimeToLive(final Duration timeToLive) {
    defaultMessageTimeToLive = timeToLive;
    return this;
  }

  @Override
  public ZeebeClientBuilder defaultRequestTimeout(final Duration requestTimeout) {
    defaultRequestTimeout = requestTimeout;
    return this;
  }

  @Override
  public ZeebeClientBuilder usePlaintext() {
    usePlaintextConnection = true;
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
  public ZeebeClientBuilder withJsonMapper(final JsonMapper jsonMapper) {
    this.jsonMapper = jsonMapper;
    return this;
  }

  @Override
  public ZeebeClientBuilder overrideAuthority(final String authority) {
    overrideAuthority = authority;
    return this;
  }

  @Override
  public ZeebeClientBuilder maxMessageSize(final int maxMessageSize) {
    this.maxMessageSize = maxMessageSize;
    return this;
  }

  @Override
  public ZeebeClientBuilder maxMetadataSize(final int maxMetadataSize) {
    this.maxMetadataSize = maxMetadataSize;
    return this;
  }

  @Override
  public ZeebeClientBuilder defaultJobWorkerStreamEnabled(final boolean streamEnabled) {
    this.streamEnabled = streamEnabled;
    return this;
  }

  @Override
  public ZeebeClientBuilder useDefaultRetryPolicy(final boolean useDefaultRetryPolicy) {
    this.useDefaultRetryPolicy = useDefaultRetryPolicy;
    return this;
  }

  @Override
  public ZeebeClientBuilder preferRestOverGrpc(final boolean preferRestOverGrpc) {
    this.preferRestOverGrpc = preferRestOverGrpc;
    return this;
  }

  @Override
  public ZeebeClient build() {
    if (applyEnvironmentVariableOverrides) {
      applyOverrides();
    }

    if (!grpcAddressUsed) {
      final String scheme = usePlaintextConnection ? "http://" : "https://";
      grpcAddress(getURIFromString(scheme + getGatewayAddress()));
    }

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

    if (Environment.system().isDefined(OVERRIDE_AUTHORITY_VAR)) {
      overrideAuthority(Environment.system().get(OVERRIDE_AUTHORITY_VAR));
    }

    if (shouldUseDefaultCredentialsProvider()) {
      credentialsProvider = createDefaultCredentialsProvider();
    }

    if (Environment.system().isDefined(MAX_MESSAGE_SIZE)) {
      maxMessageSize(DataSizeUtil.parse(Environment.system().get(MAX_MESSAGE_SIZE)));
    }

    if (Environment.system().isDefined(MAX_METADATA_SIZE)) {
      maxMetadataSize(DataSizeUtil.parse(Environment.system().get(MAX_METADATA_SIZE)));
    }

    if (Environment.system().isDefined(GRPC_ADDRESS_VAR)) {
      final URI grpcAddr = getURIFromString(Environment.system().get(GRPC_ADDRESS_VAR));
      grpcAddress(grpcAddr);
    }

    if (Environment.system().isDefined(REST_ADDRESS_VAR)) {
      final URI restAddr = getURIFromString(Environment.system().get(REST_ADDRESS_VAR));
      restAddress(restAddr);
    }

    if (Environment.system().isDefined(PREFER_REST_VAR)) {
      preferRestOverGrpc(Environment.system().getBoolean(PREFER_REST_VAR));
    }

    if (Environment.system().isDefined(DEFAULT_TENANT_ID_VAR)) {
      defaultTenantId(Environment.system().get(DEFAULT_TENANT_ID_VAR));
    }

    if (Environment.system().isDefined(DEFAULT_JOB_WORKER_TENANT_IDS_VAR)) {
      final String tenantIdsList = Environment.system().get(DEFAULT_JOB_WORKER_TENANT_IDS_VAR);
      final List<String> tenantIds = Arrays.asList(tenantIdsList.split(TENANT_ID_LIST_SEPARATOR));
      defaultJobWorkerTenantIds(tenantIds);
    }

    if (Environment.system().isDefined(ZEEBE_CLIENT_WORKER_STREAM_ENABLED)) {
      defaultJobWorkerStreamEnabled(
          Boolean.parseBoolean(Environment.system().get(ZEEBE_CLIENT_WORKER_STREAM_ENABLED)));
    }

    if (Environment.system().isDefined(USE_DEFAULT_RETRY_POLICY_VAR)) {
      useDefaultRetryPolicy(
          Boolean.parseBoolean(Environment.system().get(USE_DEFAULT_RETRY_POLICY_VAR)));
    }
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder();

    appendProperty(sb, "gatewayAddress", gatewayAddress);
    appendProperty(sb, "grpcAddress", grpcAddress);
    appendProperty(sb, "restAddress", restAddress);
    appendProperty(sb, "defaultTenantId", defaultTenantId);
    appendProperty(sb, "jobWorkerMaxJobsActive", jobWorkerMaxJobsActive);
    appendProperty(sb, "numJobWorkerExecutionThreads", numJobWorkerExecutionThreads);
    appendProperty(sb, "defaultJobWorkerName", defaultJobWorkerName);
    appendProperty(sb, "defaultJobTimeout", defaultJobTimeout);
    appendProperty(sb, "defaultJobPollInterval", defaultJobPollInterval);
    appendProperty(sb, "defaultMessageTimeToLive", defaultMessageTimeToLive);
    appendProperty(sb, "defaultRequestTimeout", defaultRequestTimeout);
    appendProperty(sb, "overrideAuthority", overrideAuthority);
    appendProperty(sb, "maxMessageSize", maxMessageSize);
    appendProperty(sb, "maxMetadataSize", maxMetadataSize);
    appendProperty(sb, "jobWorkerExecutor", jobWorkerExecutor);
    appendProperty(sb, "ownsJobWorkerExecutor", ownsJobWorkerExecutor);
    appendProperty(sb, "streamEnabled", streamEnabled);
    appendProperty(sb, "preferRestOverGrpc", preferRestOverGrpc);

    return sb.toString();
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
    final int separatorIndex = gatewayAddress.lastIndexOf(':');
    if (separatorIndex > 0) {
      builder.audience(gatewayAddress.substring(0, separatorIndex));
    }

    return builder.build();
  }

  private static URI getURIFromString(final String uri) {
    try {
      return new URI(uri);
    } catch (final URISyntaxException e) {
      throw new IllegalArgumentException("Failed to parse URI string", e);
    }
  }
}

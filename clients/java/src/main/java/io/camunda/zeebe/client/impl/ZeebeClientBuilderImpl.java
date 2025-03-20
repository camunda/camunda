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

import static io.camunda.client.impl.util.ClientPropertiesValidationUtils.checkIfUriIsAbsolute;
import static io.camunda.zeebe.client.ClientProperties.APPLY_ENVIRONMENT_VARIABLES_OVERRIDES;
import static io.camunda.zeebe.client.ClientProperties.CA_CERTIFICATE_PATH;
import static io.camunda.zeebe.client.ClientProperties.DEFAULT_JOB_POLL_INTERVAL;
import static io.camunda.zeebe.client.ClientProperties.DEFAULT_JOB_TIMEOUT;
import static io.camunda.zeebe.client.ClientProperties.DEFAULT_JOB_WORKER_NAME;
import static io.camunda.zeebe.client.ClientProperties.DEFAULT_JOB_WORKER_TENANT_IDS;
import static io.camunda.zeebe.client.ClientProperties.DEFAULT_MESSAGE_TIME_TO_LIVE;
import static io.camunda.zeebe.client.ClientProperties.DEFAULT_REQUEST_TIMEOUT;
import static io.camunda.zeebe.client.ClientProperties.DEFAULT_TENANT_ID;
import static io.camunda.zeebe.client.ClientProperties.GATEWAY_ADDRESS;
import static io.camunda.zeebe.client.ClientProperties.GRPC_ADDRESS;
import static io.camunda.zeebe.client.ClientProperties.JOB_WORKER_EXECUTION_THREADS;
import static io.camunda.zeebe.client.ClientProperties.JOB_WORKER_MAX_JOBS_ACTIVE;
import static io.camunda.zeebe.client.ClientProperties.KEEP_ALIVE;
import static io.camunda.zeebe.client.ClientProperties.MAX_MESSAGE_SIZE;
import static io.camunda.zeebe.client.ClientProperties.MAX_METADATA_SIZE;
import static io.camunda.zeebe.client.ClientProperties.OVERRIDE_AUTHORITY;
import static io.camunda.zeebe.client.ClientProperties.PREFER_REST_OVER_GRPC;
import static io.camunda.zeebe.client.ClientProperties.REST_ADDRESS;
import static io.camunda.zeebe.client.ClientProperties.STREAM_ENABLED;
import static io.camunda.zeebe.client.ClientProperties.USE_DEFAULT_RETRY_POLICY;
import static io.camunda.zeebe.client.ClientProperties.USE_PLAINTEXT_CONNECTION;
import static io.camunda.zeebe.client.impl.ZeebeClientEnvironmentVariables.CA_CERTIFICATE_VAR;
import static io.camunda.zeebe.client.impl.ZeebeClientEnvironmentVariables.DEFAULT_JOB_WORKER_TENANT_IDS_VAR;
import static io.camunda.zeebe.client.impl.ZeebeClientEnvironmentVariables.DEFAULT_TENANT_ID_VAR;
import static io.camunda.zeebe.client.impl.ZeebeClientEnvironmentVariables.GRPC_ADDRESS_VAR;
import static io.camunda.zeebe.client.impl.ZeebeClientEnvironmentVariables.KEEP_ALIVE_VAR;
import static io.camunda.zeebe.client.impl.ZeebeClientEnvironmentVariables.OAUTH_ENV_CLIENT_ID;
import static io.camunda.zeebe.client.impl.ZeebeClientEnvironmentVariables.OAUTH_ENV_CLIENT_SECRET;
import static io.camunda.zeebe.client.impl.ZeebeClientEnvironmentVariables.OVERRIDE_AUTHORITY_VAR;
import static io.camunda.zeebe.client.impl.ZeebeClientEnvironmentVariables.PLAINTEXT_CONNECTION_VAR;
import static io.camunda.zeebe.client.impl.ZeebeClientEnvironmentVariables.PREFER_REST_VAR;
import static io.camunda.zeebe.client.impl.ZeebeClientEnvironmentVariables.REST_ADDRESS_VAR;
import static io.camunda.zeebe.client.impl.ZeebeClientEnvironmentVariables.USE_DEFAULT_RETRY_POLICY_VAR;
import static io.camunda.zeebe.client.impl.ZeebeClientEnvironmentVariables.ZEEBE_CLIENT_WORKER_STREAM_ENABLED;
import static io.camunda.zeebe.client.impl.util.DataSizeUtil.ONE_KB;
import static io.camunda.zeebe.client.impl.util.DataSizeUtil.ONE_MB;

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
import org.apache.hc.client5.http.async.AsyncExecChainHandler;

public final class ZeebeClientBuilderImpl implements ZeebeClientBuilder, ZeebeClientConfiguration {

  public static final String DEFAULT_GATEWAY_ADDRESS = "0.0.0.0:26500";
  public static final URI DEFAULT_GRPC_ADDRESS =
      getURIFromString("http://" + DEFAULT_GATEWAY_ADDRESS);
  public static final URI DEFAULT_REST_ADDRESS = getURIFromString("http://0.0.0.0:8080");
  public static final String DEFAULT_JOB_WORKER_NAME_VAR = "default";
  private static final String TENANT_ID_LIST_SEPARATOR = ",";
  private static final boolean DEFAULT_PREFER_REST_OVER_GRPC = false;

  private boolean applyEnvironmentVariableOverrides = true;

  private final List<ClientInterceptor> interceptors = new ArrayList<>();
  private final List<AsyncExecChainHandler> chainHandlers = new ArrayList<>();
  private String gatewayAddress = DEFAULT_GATEWAY_ADDRESS;
  private URI restAddress = DEFAULT_REST_ADDRESS;
  private URI grpcAddress = DEFAULT_GRPC_ADDRESS;
  private boolean preferRestOverGrpc = DEFAULT_PREFER_REST_OVER_GRPC;
  private String defaultTenantId = CommandWithTenantStep.DEFAULT_TENANT_IDENTIFIER;
  private List<String> defaultJobWorkerTenantIds =
      Collections.singletonList(CommandWithTenantStep.DEFAULT_TENANT_IDENTIFIER);
  private int jobWorkerMaxJobsActive = 32;
  private int numJobWorkerExecutionThreads = 1;
  private String defaultJobWorkerName = DEFAULT_JOB_WORKER_NAME_VAR;
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
  private boolean grpcAddressUsed = true;
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
  public List<AsyncExecChainHandler> getChainHandlers() {
    return chainHandlers;
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
    BuilderUtils.applyIfNotNull(
        properties,
        APPLY_ENVIRONMENT_VARIABLES_OVERRIDES,
        value -> applyEnvironmentVariableOverrides(Boolean.parseBoolean(value)));

    BuilderUtils.applyIfNotNull(
        properties, GRPC_ADDRESS, value -> grpcAddress(getURIFromString(value)));

    BuilderUtils.applyIfNotNull(
        properties, REST_ADDRESS, value -> restAddress(getURIFromString(value)));

    BuilderUtils.applyIfNotNull(properties, GATEWAY_ADDRESS, this::gatewayAddress);

    BuilderUtils.applyIfNotNull(
        properties,
        PREFER_REST_OVER_GRPC,
        value -> preferRestOverGrpc(Boolean.parseBoolean(value)));

    BuilderUtils.applyIfNotNull(properties, DEFAULT_TENANT_ID, this::defaultTenantId);

    BuilderUtils.applyIfNotNull(
        properties,
        DEFAULT_JOB_WORKER_TENANT_IDS,
        value -> {
          final List<String> tenantIds = Arrays.asList(value.split(TENANT_ID_LIST_SEPARATOR));
          defaultJobWorkerTenantIds(tenantIds);
        });

    BuilderUtils.applyIfNotNull(
        properties,
        JOB_WORKER_EXECUTION_THREADS,
        value -> numJobWorkerExecutionThreads(Integer.parseInt(value)));

    BuilderUtils.applyIfNotNull(
        properties,
        JOB_WORKER_MAX_JOBS_ACTIVE,
        value -> defaultJobWorkerMaxJobsActive(Integer.parseInt(value)));

    BuilderUtils.applyIfNotNull(properties, DEFAULT_JOB_WORKER_NAME, this::defaultJobWorkerName);

    BuilderUtils.applyIfNotNull(
        properties,
        DEFAULT_JOB_TIMEOUT,
        value -> defaultJobTimeout(Duration.ofMillis(Long.parseLong(value))));

    BuilderUtils.applyIfNotNull(
        properties,
        DEFAULT_JOB_POLL_INTERVAL,
        value -> defaultJobPollInterval(Duration.ofMillis(Long.parseLong(value))));

    BuilderUtils.applyIfNotNull(
        properties,
        DEFAULT_MESSAGE_TIME_TO_LIVE,
        value -> defaultMessageTimeToLive(Duration.ofMillis(Long.parseLong(value))));

    BuilderUtils.applyIfNotNull(
        properties,
        DEFAULT_REQUEST_TIMEOUT,
        value -> defaultRequestTimeout(Duration.ofMillis(Long.parseLong(value))));

    BuilderUtils.applyIfNotNull(
        properties,
        USE_PLAINTEXT_CONNECTION,
        value -> {
          /**
           * The following condition is phrased in this particular way in order to be backwards
           * compatible with older versions of the software. In older versions the content of the
           * property was not interpreted. It was assumed to be true, whenever it was set. Because
           * of that, code examples in this code base set the flag to an empty string. By phrasing
           * the condition this way, the old code will still work with this new implementation. Only
           * if somebody deliberately sets the flag to false, the behavior will change
           */
          if (!"false".equalsIgnoreCase(value)) {
            usePlaintext();
          }
        });

    BuilderUtils.applyIfNotNull(properties, CA_CERTIFICATE_PATH, this::caCertificatePath);

    BuilderUtils.applyIfNotNull(properties, KEEP_ALIVE, this::keepAlive);

    BuilderUtils.applyIfNotNull(properties, OVERRIDE_AUTHORITY, this::overrideAuthority);

    BuilderUtils.applyIfNotNull(
        properties, MAX_MESSAGE_SIZE, value -> maxMessageSize(DataSizeUtil.parse(value)));

    BuilderUtils.applyIfNotNull(
        properties, MAX_METADATA_SIZE, value -> maxMetadataSize(DataSizeUtil.parse(value)));

    BuilderUtils.applyIfNotNull(
        properties,
        STREAM_ENABLED,
        value -> defaultJobWorkerStreamEnabled(Boolean.parseBoolean(value)));

    BuilderUtils.applyIfNotNull(
        properties,
        USE_DEFAULT_RETRY_POLICY,
        value -> useDefaultRetryPolicy(Boolean.parseBoolean(value)));

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
    grpcAddressUsed = false;
    return this;
  }

  @Override
  public ZeebeClientBuilder restAddress(final URI restAddress) {
    checkIfUriIsAbsolute(restAddress, "restAddress");
    this.restAddress = restAddress;
    return this;
  }

  @Override
  public ZeebeClientBuilder grpcAddress(final URI grpcAddress) {
    checkIfUriIsAbsolute(grpcAddress, "grpcAddress");
    this.grpcAddress = grpcAddress;
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
  public ZeebeClientBuilder withChainHandlers(final AsyncExecChainHandler... chainHandler) {
    chainHandlers.addAll(Arrays.asList(chainHandler));
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
    BuilderUtils.applyIfNotNull(
        PLAINTEXT_CONNECTION_VAR, value -> usePlaintextConnection = Boolean.parseBoolean(value));

    BuilderUtils.applyIfNotNull(CA_CERTIFICATE_VAR, this::caCertificatePath);

    BuilderUtils.applyIfNotNull(KEEP_ALIVE_VAR, this::keepAlive);

    BuilderUtils.applyIfNotNull(OVERRIDE_AUTHORITY_VAR, this::overrideAuthority);

    if (shouldUseDefaultCredentialsProvider()) {
      credentialsProvider = createDefaultCredentialsProvider();
    }

    BuilderUtils.applyIfNotNull(GRPC_ADDRESS_VAR, value -> grpcAddress(getURIFromString(value)));

    BuilderUtils.applyIfNotNull(REST_ADDRESS_VAR, value -> restAddress(getURIFromString(value)));

    BuilderUtils.applyIfNotNull(
        PREFER_REST_VAR, value -> preferRestOverGrpc(Boolean.parseBoolean(value)));

    BuilderUtils.applyIfNotNull(DEFAULT_TENANT_ID_VAR, this::defaultTenantId);

    BuilderUtils.applyIfNotNull(
        DEFAULT_JOB_WORKER_TENANT_IDS_VAR,
        value -> {
          final List<String> tenantIds = Arrays.asList(value.split(TENANT_ID_LIST_SEPARATOR));
          defaultJobWorkerTenantIds(tenantIds);
        });

    BuilderUtils.applyIfNotNull(
        ZEEBE_CLIENT_WORKER_STREAM_ENABLED,
        value -> defaultJobWorkerStreamEnabled(Boolean.parseBoolean(value)));

    BuilderUtils.applyIfNotNull(
        USE_DEFAULT_RETRY_POLICY_VAR, value -> useDefaultRetryPolicy(Boolean.parseBoolean(value)));
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder();

    BuilderUtils.appendProperty(sb, "gatewayAddress", gatewayAddress);
    BuilderUtils.appendProperty(sb, "grpcAddress", grpcAddress);
    BuilderUtils.appendProperty(sb, "restAddress", restAddress);
    BuilderUtils.appendProperty(sb, "defaultTenantId", defaultTenantId);
    BuilderUtils.appendProperty(sb, "jobWorkerMaxJobsActive", jobWorkerMaxJobsActive);
    BuilderUtils.appendProperty(sb, "numJobWorkerExecutionThreads", numJobWorkerExecutionThreads);
    BuilderUtils.appendProperty(sb, "defaultJobWorkerName", defaultJobWorkerName);
    BuilderUtils.appendProperty(sb, "defaultJobTimeout", defaultJobTimeout);
    BuilderUtils.appendProperty(sb, "defaultJobPollInterval", defaultJobPollInterval);
    BuilderUtils.appendProperty(sb, "defaultMessageTimeToLive", defaultMessageTimeToLive);
    BuilderUtils.appendProperty(sb, "defaultRequestTimeout", defaultRequestTimeout);
    BuilderUtils.appendProperty(sb, "overrideAuthority", overrideAuthority);
    BuilderUtils.appendProperty(sb, "maxMessageSize", maxMessageSize);
    BuilderUtils.appendProperty(sb, "maxMetadataSize", maxMetadataSize);
    BuilderUtils.appendProperty(sb, "jobWorkerExecutor", jobWorkerExecutor);
    BuilderUtils.appendProperty(sb, "ownsJobWorkerExecutor", ownsJobWorkerExecutor);
    BuilderUtils.appendProperty(sb, "streamEnabled", streamEnabled);
    BuilderUtils.appendProperty(sb, "preferRestOverGrpc", preferRestOverGrpc);

    return sb.toString();
  }

  private boolean shouldUseDefaultCredentialsProvider() {
    return credentialsProvider == null
        && Environment.system().get(OAUTH_ENV_CLIENT_ID) != null
        && Environment.system().get(OAUTH_ENV_CLIENT_SECRET) != null;
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

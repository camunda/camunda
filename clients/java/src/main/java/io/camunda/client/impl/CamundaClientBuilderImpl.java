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
package io.camunda.client.impl;

import static io.camunda.client.ClientProperties.APPLY_ENVIRONMENT_VARIABLES_OVERRIDES;
import static io.camunda.client.ClientProperties.CA_CERTIFICATE_PATH;
import static io.camunda.client.ClientProperties.DEFAULT_JOB_POLL_INTERVAL;
import static io.camunda.client.ClientProperties.DEFAULT_JOB_TIMEOUT;
import static io.camunda.client.ClientProperties.DEFAULT_JOB_WORKER_NAME;
import static io.camunda.client.ClientProperties.DEFAULT_JOB_WORKER_TENANT_IDS;
import static io.camunda.client.ClientProperties.DEFAULT_MESSAGE_TIME_TO_LIVE;
import static io.camunda.client.ClientProperties.DEFAULT_REQUEST_TIMEOUT;
import static io.camunda.client.ClientProperties.DEFAULT_TENANT_ID;
import static io.camunda.client.ClientProperties.GATEWAY_ADDRESS;
import static io.camunda.client.ClientProperties.GRPC_ADDRESS;
import static io.camunda.client.ClientProperties.JOB_WORKER_EXECUTION_THREADS;
import static io.camunda.client.ClientProperties.JOB_WORKER_MAX_JOBS_ACTIVE;
import static io.camunda.client.ClientProperties.KEEP_ALIVE;
import static io.camunda.client.ClientProperties.MAX_MESSAGE_SIZE;
import static io.camunda.client.ClientProperties.MAX_METADATA_SIZE;
import static io.camunda.client.ClientProperties.OVERRIDE_AUTHORITY;
import static io.camunda.client.ClientProperties.PREFER_REST_OVER_GRPC;
import static io.camunda.client.ClientProperties.REST_ADDRESS;
import static io.camunda.client.ClientProperties.STREAM_ENABLED;
import static io.camunda.client.ClientProperties.USE_DEFAULT_RETRY_POLICY;
import static io.camunda.client.ClientProperties.USE_PLAINTEXT_CONNECTION;
import static io.camunda.client.impl.BuilderUtils.appendProperty;
import static io.camunda.client.impl.BuilderUtils.applyIfNotNull;
import static io.camunda.client.impl.util.DataSizeUtil.ONE_KB;
import static io.camunda.client.impl.util.DataSizeUtil.ONE_MB;

import io.camunda.client.CamundaClient;
import io.camunda.client.CamundaClientBuilder;
import io.camunda.client.CamundaClientConfiguration;
import io.camunda.client.CredentialsProvider;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.command.CommandWithTenantStep;
import io.camunda.client.impl.oauth.OAuthCredentialsProviderBuilder;
import io.camunda.client.impl.util.DataSizeUtil;
import io.camunda.client.impl.util.Environment;
import io.camunda.zeebe.client.ClientProperties;
import io.camunda.zeebe.client.impl.ZeebeClientBuilderImpl;
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

public final class CamundaClientBuilderImpl
    implements CamundaClientBuilder, CamundaClientConfiguration {

  public static final String PLAINTEXT_CONNECTION_VAR = "CAMUNDA_INSECURE_CONNECTION";
  public static final String CA_CERTIFICATE_VAR = "CAMUNDA_CA_CERTIFICATE_PATH";
  public static final String KEEP_ALIVE_VAR = "CAMUNDA_KEEP_ALIVE";
  public static final String OVERRIDE_AUTHORITY_VAR = "CAMUNDA_OVERRIDE_AUTHORITY";
  public static final String CAMUNDA_CLIENT_WORKER_STREAM_ENABLED =
      "CAMUNDA_CLIENT_WORKER_STREAM_ENABLED";
  public static final String DEFAULT_GATEWAY_ADDRESS = "0.0.0.0:26500";
  public static final URI DEFAULT_GRPC_ADDRESS =
      getURIFromString("https://" + DEFAULT_GATEWAY_ADDRESS);
  public static final URI DEFAULT_REST_ADDRESS = getURIFromString("https://0.0.0.0:8080");
  public static final String REST_ADDRESS_VAR = "CAMUNDA_REST_ADDRESS";
  public static final String GRPC_ADDRESS_VAR = "CAMUNDA_GRPC_ADDRESS";
  public static final String PREFER_REST_VAR = "CAMUNDA_PREFER_REST";
  public static final String DEFAULT_TENANT_ID_VAR = "CAMUNDA_DEFAULT_TENANT_ID";
  public static final String DEFAULT_JOB_WORKER_TENANT_IDS_VAR =
      "CAMUNDA_DEFAULT_JOB_WORKER_TENANT_IDS";
  public static final String DEFAULT_JOB_WORKER_NAME_VAR = "default";
  public static final String USE_DEFAULT_RETRY_POLICY_VAR =
      "CAMUNDA_CLIENT_USE_DEFAULT_RETRY_POLICY";
  public static final String CAMUNDA_MAX_MESSAGE_SIZE_VAR = "CAMUNDA_MAX_MESSAGE_SIZE";
  public static final String CAMUNDA_MAX_METADATA_SIZE_VAR = "CAMUNDA_MAX_METADATA_SIZE";
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
  private String defaultJobWorkerName = DEFAULT_JOB_WORKER_NAME_VAR;
  private Duration defaultJobTimeout = Duration.ofMinutes(5);
  private Duration defaultJobPollInterval = Duration.ofMillis(100);
  private Duration defaultMessageTimeToLive = Duration.ofHours(1);
  private Duration defaultRequestTimeout = Duration.ofSeconds(10);
  private boolean usePlaintextConnection = false;
  private String certificatePath;
  private CredentialsProvider credentialsProvider;
  private Duration keepAlive = Duration.ofSeconds(45);
  private JsonMapper jsonMapper = new CamundaObjectMapper();
  private String overrideAuthority;
  private int maxMessageSize = 4 * ONE_MB;
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
  public CamundaClientBuilder withProperties(final Properties properties) {
    applyIfNotNull(
        properties,
        APPLY_ENVIRONMENT_VARIABLES_OVERRIDES,
        ClientProperties.APPLY_ENVIRONMENT_VARIABLES_OVERRIDES,
        value -> applyEnvironmentVariableOverrides(Boolean.parseBoolean(value)));

    applyIfNotNull(
        properties,
        GRPC_ADDRESS,
        ClientProperties.GRPC_ADDRESS,
        value -> grpcAddress(getURIFromString(value)));

    applyIfNotNull(
        properties,
        REST_ADDRESS,
        ClientProperties.REST_ADDRESS,
        value -> restAddress(getURIFromString(value)));

    applyIfNotNull(
        properties, GATEWAY_ADDRESS, ClientProperties.GATEWAY_ADDRESS, this::gatewayAddress);

    applyIfNotNull(
        properties,
        PREFER_REST_OVER_GRPC,
        ClientProperties.PREFER_REST_OVER_GRPC,
        value -> preferRestOverGrpc(Boolean.parseBoolean(value)));

    applyIfNotNull(
        properties, DEFAULT_TENANT_ID, ClientProperties.DEFAULT_TENANT_ID, this::defaultTenantId);

    applyIfNotNull(
        properties,
        DEFAULT_JOB_WORKER_TENANT_IDS,
        ClientProperties.DEFAULT_JOB_WORKER_TENANT_IDS,
        value -> {
          final List<String> tenantIds = Arrays.asList(value.split(TENANT_ID_LIST_SEPARATOR));
          defaultJobWorkerTenantIds(tenantIds);
        });

    applyIfNotNull(
        properties,
        JOB_WORKER_EXECUTION_THREADS,
        ClientProperties.JOB_WORKER_EXECUTION_THREADS,
        value -> numJobWorkerExecutionThreads(Integer.parseInt(value)));

    applyIfNotNull(
        properties,
        JOB_WORKER_MAX_JOBS_ACTIVE,
        ClientProperties.JOB_WORKER_MAX_JOBS_ACTIVE,
        value -> defaultJobWorkerMaxJobsActive(Integer.parseInt(value)));

    applyIfNotNull(
        properties,
        DEFAULT_JOB_WORKER_NAME,
        ClientProperties.DEFAULT_JOB_WORKER_NAME,
        this::defaultJobWorkerName);

    applyIfNotNull(
        properties,
        DEFAULT_JOB_TIMEOUT,
        ClientProperties.DEFAULT_JOB_TIMEOUT,
        value -> defaultJobTimeout(Duration.ofMillis(Long.parseLong(value))));

    applyIfNotNull(
        properties,
        DEFAULT_JOB_POLL_INTERVAL,
        ClientProperties.DEFAULT_JOB_POLL_INTERVAL,
        value -> defaultJobPollInterval(Duration.ofMillis(Long.parseLong(value))));

    applyIfNotNull(
        properties,
        DEFAULT_MESSAGE_TIME_TO_LIVE,
        ClientProperties.DEFAULT_MESSAGE_TIME_TO_LIVE,
        value -> defaultMessageTimeToLive(Duration.ofMillis(Long.parseLong(value))));

    applyIfNotNull(
        properties,
        DEFAULT_REQUEST_TIMEOUT,
        ClientProperties.DEFAULT_REQUEST_TIMEOUT,
        value -> defaultRequestTimeout(Duration.ofSeconds(Long.parseLong(value))));

    applyIfNotNull(
        properties,
        USE_PLAINTEXT_CONNECTION,
        ClientProperties.USE_PLAINTEXT_CONNECTION,
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

    applyIfNotNull(
        properties,
        CA_CERTIFICATE_PATH,
        ClientProperties.CA_CERTIFICATE_PATH,
        this::caCertificatePath);

    applyIfNotNull(properties, KEEP_ALIVE, ClientProperties.KEEP_ALIVE, this::keepAlive);

    applyIfNotNull(
        properties,
        OVERRIDE_AUTHORITY,
        ClientProperties.OVERRIDE_AUTHORITY,
        this::overrideAuthority);

    applyIfNotNull(
        properties,
        MAX_MESSAGE_SIZE,
        ClientProperties.MAX_MESSAGE_SIZE,
        value -> maxMessageSize(DataSizeUtil.parse(value)));

    applyIfNotNull(
        properties,
        MAX_METADATA_SIZE,
        ClientProperties.MAX_METADATA_SIZE,
        value -> maxMetadataSize(DataSizeUtil.parse(value)));

    applyIfNotNull(
        properties,
        STREAM_ENABLED,
        ClientProperties.STREAM_ENABLED,
        value -> defaultJobWorkerStreamEnabled(Boolean.parseBoolean(value)));

    applyIfNotNull(
        properties,
        USE_DEFAULT_RETRY_POLICY,
        ClientProperties.USE_DEFAULT_RETRY_POLICY,
        value -> useDefaultRetryPolicy(Boolean.parseBoolean(value)));

    return this;
  }

  @Override
  public CamundaClientBuilder applyEnvironmentVariableOverrides(
      final boolean applyEnvironmentVariableOverrides) {
    this.applyEnvironmentVariableOverrides = applyEnvironmentVariableOverrides;
    return this;
  }

  @Override
  public CamundaClientBuilder gatewayAddress(final String gatewayAddress) {
    this.gatewayAddress = gatewayAddress;
    return this;
  }

  @Override
  public CamundaClientBuilder restAddress(final URI restAddress) {
    this.restAddress = restAddress;
    return this;
  }

  @Override
  public CamundaClientBuilder grpcAddress(final URI grpcAddress) {
    this.grpcAddress = grpcAddress;
    grpcAddressUsed = true;
    return this;
  }

  @Override
  public CamundaClientBuilder defaultTenantId(final String tenantId) {
    defaultTenantId = tenantId;
    return this;
  }

  @Override
  public CamundaClientBuilder defaultJobWorkerTenantIds(final List<String> tenantIds) {
    defaultJobWorkerTenantIds = tenantIds;
    return this;
  }

  @Override
  public CamundaClientBuilder defaultJobWorkerMaxJobsActive(final int maxJobsActive) {
    jobWorkerMaxJobsActive = maxJobsActive;
    return this;
  }

  @Override
  public CamundaClientBuilder numJobWorkerExecutionThreads(final int numSubscriptionThreads) {
    numJobWorkerExecutionThreads = numSubscriptionThreads;
    return this;
  }

  @Override
  public CamundaClientBuilder jobWorkerExecutor(
      final ScheduledExecutorService executor, final boolean takeOwnership) {
    jobWorkerExecutor = executor;
    ownsJobWorkerExecutor = takeOwnership;
    return this;
  }

  @Override
  public CamundaClientBuilder defaultJobWorkerName(final String workerName) {
    if (workerName != null) {
      defaultJobWorkerName = workerName;
    }
    return this;
  }

  @Override
  public CamundaClientBuilder defaultJobTimeout(final Duration timeout) {
    defaultJobTimeout = timeout;
    return this;
  }

  @Override
  public CamundaClientBuilder defaultJobPollInterval(final Duration pollInterval) {
    defaultJobPollInterval = pollInterval;
    return this;
  }

  @Override
  public CamundaClientBuilder defaultMessageTimeToLive(final Duration timeToLive) {
    defaultMessageTimeToLive = timeToLive;
    return this;
  }

  @Override
  public CamundaClientBuilder defaultRequestTimeout(final Duration requestTimeout) {
    defaultRequestTimeout = requestTimeout;
    return this;
  }

  @Override
  public CamundaClientBuilder usePlaintext() {
    usePlaintextConnection = true;
    return this;
  }

  @Override
  public CamundaClientBuilder caCertificatePath(final String certificatePath) {
    this.certificatePath = certificatePath;
    return this;
  }

  @Override
  public CamundaClientBuilder credentialsProvider(final CredentialsProvider credentialsProvider) {
    this.credentialsProvider = credentialsProvider;
    return this;
  }

  @Override
  public CamundaClientBuilder keepAlive(final Duration keepAlive) {
    if (keepAlive.isNegative() || keepAlive.isZero()) {
      throw new IllegalArgumentException("The keep alive must be a positive number.");
    }

    this.keepAlive = keepAlive;
    return this;
  }

  @Override
  public CamundaClientBuilder withInterceptors(final ClientInterceptor... interceptors) {
    this.interceptors.addAll(Arrays.asList(interceptors));
    return this;
  }

  @Override
  public CamundaClientBuilder withJsonMapper(final JsonMapper jsonMapper) {
    this.jsonMapper = jsonMapper;
    return this;
  }

  @Override
  public CamundaClientBuilder overrideAuthority(final String authority) {
    overrideAuthority = authority;
    return this;
  }

  @Override
  public CamundaClientBuilder maxMessageSize(final int maxMessageSize) {
    this.maxMessageSize = maxMessageSize;
    return this;
  }

  @Override
  public CamundaClientBuilder maxMetadataSize(final int maxMetadataSize) {
    this.maxMetadataSize = maxMetadataSize;
    return this;
  }

  @Override
  public CamundaClientBuilder defaultJobWorkerStreamEnabled(final boolean streamEnabled) {
    this.streamEnabled = streamEnabled;
    return this;
  }

  @Override
  public CamundaClientBuilder useDefaultRetryPolicy(final boolean useDefaultRetryPolicy) {
    this.useDefaultRetryPolicy = useDefaultRetryPolicy;
    return this;
  }

  @Override
  public CamundaClientBuilder preferRestOverGrpc(final boolean preferRestOverGrpc) {
    this.preferRestOverGrpc = preferRestOverGrpc;
    return this;
  }

  @Override
  public CamundaClient build() {
    if (applyEnvironmentVariableOverrides) {
      applyOverrides();
    }

    if (!grpcAddressUsed) {
      final String scheme = usePlaintextConnection ? "http://" : "https://";
      grpcAddress(getURIFromString(scheme + getGatewayAddress()));
    }

    return new CamundaClientImpl(this);
  }

  private void keepAlive(final String keepAlive) {
    keepAlive(Duration.ofMillis(Long.parseUnsignedLong(keepAlive)));
  }

  private void applyOverrides() {
    applyIfNotNull(
        PLAINTEXT_CONNECTION_VAR,
        ZeebeClientBuilderImpl.PLAINTEXT_CONNECTION_VAR,
        value -> usePlaintextConnection = Boolean.parseBoolean(value));

    applyIfNotNull(
        CA_CERTIFICATE_VAR, ZeebeClientBuilderImpl.CA_CERTIFICATE_VAR, this::caCertificatePath);

    applyIfNotNull(KEEP_ALIVE_VAR, ZeebeClientBuilderImpl.KEEP_ALIVE_VAR, this::keepAlive);

    applyIfNotNull(
        OVERRIDE_AUTHORITY_VAR,
        ZeebeClientBuilderImpl.OVERRIDE_AUTHORITY_VAR,
        this::overrideAuthority);

    if (shouldUseDefaultCredentialsProvider()) {
      credentialsProvider = createDefaultCredentialsProvider();
    }

    applyIfNotNull(
        GRPC_ADDRESS_VAR,
        ZeebeClientBuilderImpl.GRPC_ADDRESS_VAR,
        value -> grpcAddress(getURIFromString(value)));

    applyIfNotNull(
        REST_ADDRESS_VAR,
        ZeebeClientBuilderImpl.REST_ADDRESS_VAR,
        value -> restAddress(getURIFromString(value)));

    applyIfNotNull(
        PREFER_REST_VAR,
        ZeebeClientBuilderImpl.PREFER_REST_VAR,
        value -> preferRestOverGrpc(Boolean.parseBoolean(value)));

    if (Environment.system().isDefined(DEFAULT_TENANT_ID_VAR)) {
      defaultTenantId(Environment.system().get(DEFAULT_TENANT_ID_VAR));
    }
    applyIfNotNull(
        DEFAULT_TENANT_ID_VAR, ZeebeClientBuilderImpl.DEFAULT_TENANT_ID_VAR, this::defaultTenantId);

    applyIfNotNull(
        DEFAULT_JOB_WORKER_TENANT_IDS_VAR,
        ZeebeClientBuilderImpl.DEFAULT_JOB_WORKER_TENANT_IDS_VAR,
        value -> {
          final List<String> tenantIds = Arrays.asList(value.split(TENANT_ID_LIST_SEPARATOR));
          defaultJobWorkerTenantIds(tenantIds);
        });

    applyIfNotNull(
        CAMUNDA_CLIENT_WORKER_STREAM_ENABLED,
        ZeebeClientBuilderImpl.ZEEBE_CLIENT_WORKER_STREAM_ENABLED,
        value -> defaultJobWorkerStreamEnabled(Boolean.parseBoolean(value)));

    applyIfNotNull(
        USE_DEFAULT_RETRY_POLICY_VAR,
        ZeebeClientBuilderImpl.USE_DEFAULT_RETRY_POLICY_VAR,
        value -> useDefaultRetryPolicy(Boolean.parseBoolean(value)));
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

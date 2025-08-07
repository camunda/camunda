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
import static io.camunda.client.ClientProperties.DEFAULT_JOB_WORKER_NAME;
import static io.camunda.client.ClientProperties.DEFAULT_MESSAGE_TIME_TO_LIVE;
import static io.camunda.client.ClientProperties.DEFAULT_TENANT_ID;
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
import static io.camunda.client.impl.BuilderUtils.applyEnvironmentValueIfNotNull;
import static io.camunda.client.impl.CamundaClientEnvironmentVariables.CAMUNDA_CLIENT_WORKER_STREAM_ENABLED;
import static io.camunda.client.impl.CamundaClientEnvironmentVariables.CA_CERTIFICATE_VAR;
import static io.camunda.client.impl.CamundaClientEnvironmentVariables.DEFAULT_JOB_WORKER_TENANT_IDS_VAR;
import static io.camunda.client.impl.CamundaClientEnvironmentVariables.DEFAULT_TENANT_ID_VAR;
import static io.camunda.client.impl.CamundaClientEnvironmentVariables.GRPC_ADDRESS_VAR;
import static io.camunda.client.impl.CamundaClientEnvironmentVariables.KEEP_ALIVE_VAR;
import static io.camunda.client.impl.CamundaClientEnvironmentVariables.OAUTH_ENV_CLIENT_ID;
import static io.camunda.client.impl.CamundaClientEnvironmentVariables.OAUTH_ENV_CLIENT_SECRET;
import static io.camunda.client.impl.CamundaClientEnvironmentVariables.OVERRIDE_AUTHORITY_VAR;
import static io.camunda.client.impl.CamundaClientEnvironmentVariables.PLAINTEXT_CONNECTION_VAR;
import static io.camunda.client.impl.CamundaClientEnvironmentVariables.PREFER_REST_VAR;
import static io.camunda.client.impl.CamundaClientEnvironmentVariables.REST_ADDRESS_VAR;
import static io.camunda.client.impl.CamundaClientEnvironmentVariables.USE_DEFAULT_RETRY_POLICY_VAR;
import static io.camunda.client.impl.util.ClientPropertiesValidationUtils.checkIfUriIsAbsolute;
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
import io.camunda.zeebe.client.impl.ZeebeClientEnvironmentVariables;
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

public final class CamundaClientBuilderImpl
    implements CamundaClientBuilder, CamundaClientConfiguration {

  public static final String DEFAULT_GATEWAY_ADDRESS = "0.0.0.0:26500";
  public static final URI DEFAULT_GRPC_ADDRESS =
      getURIFromString("http://" + DEFAULT_GATEWAY_ADDRESS);
  public static final URI DEFAULT_REST_ADDRESS = getURIFromString("http://0.0.0.0:8080");
  public static final String DEFAULT_JOB_WORKER_NAME_VAR = "default";
  public static final Duration DEFAULT_MESSAGE_TTL = Duration.ofHours(1);
  public static final boolean DEFAULT_PREFER_REST_OVER_GRPC = false;
  public static final int DEFAULT_NUM_JOB_WORKER_EXECUTION_THREADS = 1;
  public static final int DEFAULT_MAX_MESSAGE_SIZE = 5 * ONE_MB;
  public static final int DEFAULT_MAX_METADATA_SIZE = 16 * ONE_KB;
  public static final Duration DEFAULT_KEEP_ALIVE = Duration.ofSeconds(45);
  public static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(10);
  public static final Duration DEFAULT_REQUEST_TIMEOUT_OFFSET = Duration.ofSeconds(1);
  public static final List<String> DEFAULT_JOB_WORKER_TENANT_IDS =
      Collections.singletonList(CommandWithTenantStep.DEFAULT_TENANT_IDENTIFIER);
  public static final Duration DEFAULT_JOB_TIMEOUT = Duration.ofMinutes(5);
  public static final int DEFAULT_MAX_JOBS_ACTIVE = 32;
  public static final Duration DEFAULT_JOB_POLL_INTERVAL = Duration.ofMillis(100);
  public static final boolean DEFAULT_STREAM_ENABLED = false;
  private static final String TENANT_ID_LIST_SEPARATOR = ",";
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
  private int jobWorkerMaxJobsActive = DEFAULT_MAX_JOBS_ACTIVE;
  private int numJobWorkerExecutionThreads = DEFAULT_NUM_JOB_WORKER_EXECUTION_THREADS;
  private String defaultJobWorkerName = DEFAULT_JOB_WORKER_NAME_VAR;
  private Duration defaultJobTimeout = DEFAULT_JOB_TIMEOUT;
  private Duration defaultJobPollInterval = DEFAULT_JOB_POLL_INTERVAL;
  private Duration defaultMessageTimeToLive = DEFAULT_MESSAGE_TTL;
  private Duration defaultRequestTimeout = DEFAULT_REQUEST_TIMEOUT;
  private Duration defaultRequestTimeoutOffset = DEFAULT_REQUEST_TIMEOUT_OFFSET;
  private boolean usePlaintextConnection = false;
  private String certificatePath;
  private CredentialsProvider credentialsProvider;
  private Duration keepAlive = DEFAULT_KEEP_ALIVE;
  private JsonMapper jsonMapper = new CamundaObjectMapper();
  private String overrideAuthority;
  private int maxMessageSize = DEFAULT_MAX_MESSAGE_SIZE;
  private int maxMetadataSize = DEFAULT_MAX_METADATA_SIZE;
  private boolean streamEnabled = DEFAULT_STREAM_ENABLED;
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
  public Duration getDefaultRequestTimeoutOffset() {
    return defaultRequestTimeoutOffset;
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
  public CamundaClientBuilder withProperties(final Properties properties) {
    BuilderUtils.applyPropertyValueIfNotNull(
        properties,
        value -> applyEnvironmentVariableOverrides(Boolean.parseBoolean(value)),
        APPLY_ENVIRONMENT_VARIABLES_OVERRIDES,
        io.camunda.zeebe.client.ClientProperties.APPLY_ENVIRONMENT_VARIABLES_OVERRIDES);

    BuilderUtils.applyPropertyValueIfNotNull(
        properties,
        value -> grpcAddress(getURIFromString(value)),
        GRPC_ADDRESS,
        io.camunda.zeebe.client.ClientProperties.GRPC_ADDRESS);

    BuilderUtils.applyPropertyValueIfNotNull(
        properties,
        value -> restAddress(getURIFromString(value)),
        REST_ADDRESS,
        io.camunda.zeebe.client.ClientProperties.REST_ADDRESS);

    BuilderUtils.applyPropertyValueIfNotNull(
        properties, this::gatewayAddress, io.camunda.zeebe.client.ClientProperties.GATEWAY_ADDRESS);

    BuilderUtils.applyPropertyValueIfNotNull(
        properties,
        value -> preferRestOverGrpc(Boolean.parseBoolean(value)),
        PREFER_REST_OVER_GRPC,
        io.camunda.zeebe.client.ClientProperties.PREFER_REST_OVER_GRPC);

    BuilderUtils.applyPropertyValueIfNotNull(
        properties,
        this::defaultTenantId,
        DEFAULT_TENANT_ID,
        io.camunda.zeebe.client.ClientProperties.DEFAULT_TENANT_ID);

    BuilderUtils.applyPropertyValueIfNotNull(
        properties,
        value -> defaultJobWorkerTenantIds(Arrays.asList(value.split(TENANT_ID_LIST_SEPARATOR))),
        io.camunda.client.ClientProperties.DEFAULT_JOB_WORKER_TENANT_IDS,
        io.camunda.zeebe.client.ClientProperties.DEFAULT_JOB_WORKER_TENANT_IDS);

    BuilderUtils.applyPropertyValueIfNotNull(
        properties,
        value -> numJobWorkerExecutionThreads(Integer.parseInt(value)),
        JOB_WORKER_EXECUTION_THREADS,
        io.camunda.zeebe.client.ClientProperties.JOB_WORKER_EXECUTION_THREADS);

    BuilderUtils.applyPropertyValueIfNotNull(
        properties,
        value -> defaultJobWorkerMaxJobsActive(Integer.parseInt(value)),
        JOB_WORKER_MAX_JOBS_ACTIVE,
        io.camunda.zeebe.client.ClientProperties.JOB_WORKER_MAX_JOBS_ACTIVE);

    BuilderUtils.applyPropertyValueIfNotNull(
        properties, this::defaultJobWorkerName, DEFAULT_JOB_WORKER_NAME);

    BuilderUtils.applyPropertyValueIfNotNull(
        properties,
        value -> defaultJobTimeout(Duration.ofMillis(Long.parseLong(value))),
        io.camunda.client.ClientProperties.DEFAULT_JOB_TIMEOUT,
        io.camunda.zeebe.client.ClientProperties.DEFAULT_JOB_TIMEOUT);

    BuilderUtils.applyPropertyValueIfNotNull(
        properties,
        value -> defaultJobPollInterval(Duration.ofMillis(Long.parseLong(value))),
        io.camunda.client.ClientProperties.DEFAULT_JOB_POLL_INTERVAL,
        io.camunda.zeebe.client.ClientProperties.DEFAULT_JOB_POLL_INTERVAL);

    BuilderUtils.applyPropertyValueIfNotNull(
        properties,
        value -> defaultMessageTimeToLive(Duration.ofMillis(Long.parseLong(value))),
        DEFAULT_MESSAGE_TIME_TO_LIVE,
        io.camunda.zeebe.client.ClientProperties.DEFAULT_MESSAGE_TIME_TO_LIVE);

    BuilderUtils.applyPropertyValueIfNotNull(
        properties,
        value -> defaultRequestTimeout(Duration.ofMillis(Long.parseLong(value))),
        io.camunda.client.ClientProperties.DEFAULT_REQUEST_TIMEOUT,
        io.camunda.zeebe.client.ClientProperties.DEFAULT_REQUEST_TIMEOUT);

    BuilderUtils.applyPropertyValueIfNotNull(
        properties,
        value -> defaultRequestTimeoutOffset(Duration.ofMillis(Long.parseLong(value))),
        io.camunda.client.ClientProperties.DEFAULT_REQUEST_TIMEOUT_OFFSET);

    BuilderUtils.applyPropertyValueIfNotNull(
        properties,
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
        },
        USE_PLAINTEXT_CONNECTION,
        io.camunda.zeebe.client.ClientProperties.USE_PLAINTEXT_CONNECTION);

    BuilderUtils.applyPropertyValueIfNotNull(
        properties,
        this::caCertificatePath,
        CA_CERTIFICATE_PATH,
        io.camunda.zeebe.client.ClientProperties.CA_CERTIFICATE_PATH);

    BuilderUtils.applyPropertyValueIfNotNull(properties, this::keepAlive, KEEP_ALIVE);

    BuilderUtils.applyPropertyValueIfNotNull(
        properties,
        this::overrideAuthority,
        OVERRIDE_AUTHORITY,
        io.camunda.zeebe.client.ClientProperties.OVERRIDE_AUTHORITY);

    BuilderUtils.applyPropertyValueIfNotNull(
        properties,
        value -> maxMessageSize(DataSizeUtil.parse(value)),
        MAX_MESSAGE_SIZE,
        io.camunda.zeebe.client.ClientProperties.MAX_MESSAGE_SIZE);

    BuilderUtils.applyPropertyValueIfNotNull(
        properties,
        value -> maxMetadataSize(DataSizeUtil.parse(value)),
        MAX_METADATA_SIZE,
        io.camunda.zeebe.client.ClientProperties.MAX_METADATA_SIZE);

    BuilderUtils.applyPropertyValueIfNotNull(
        properties,
        value -> defaultJobWorkerStreamEnabled(Boolean.parseBoolean(value)),
        STREAM_ENABLED,
        io.camunda.zeebe.client.ClientProperties.STREAM_ENABLED);

    BuilderUtils.applyPropertyValueIfNotNull(
        properties,
        value -> useDefaultRetryPolicy(Boolean.parseBoolean(value)),
        USE_DEFAULT_RETRY_POLICY,
        io.camunda.zeebe.client.ClientProperties.USE_DEFAULT_RETRY_POLICY);

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
    grpcAddressUsed = false;
    return this;
  }

  @Override
  public CamundaClientBuilder restAddress(final URI restAddress) {
    checkIfUriIsAbsolute(restAddress, "restAddress");
    this.restAddress = restAddress;
    return this;
  }

  @Override
  public CamundaClientBuilder grpcAddress(final URI grpcAddress) {
    checkIfUriIsAbsolute(grpcAddress, "grpcAddress");
    this.grpcAddress = grpcAddress;
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
  public CamundaClientBuilder defaultRequestTimeoutOffset(final Duration requestTimeoutOffset) {
    defaultRequestTimeoutOffset = requestTimeoutOffset;
    return this;
  }

  @Override
  public CamundaClientBuilder usePlaintext() {
    return usePlaintext(true);
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
  public CamundaClientBuilder withChainHandlers(final AsyncExecChainHandler... chainHandler) {
    chainHandlers.addAll(Arrays.asList(chainHandler));
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

  private CamundaClientBuilder usePlaintext(final boolean usePlaintext) {
    usePlaintextConnection = usePlaintext;
    return this;
  }

  private void keepAlive(final String keepAlive) {
    keepAlive(Duration.ofMillis(Long.parseUnsignedLong(keepAlive)));
  }

  private void applyOverrides() {
    applyEnvironmentValueIfNotNull(
        value -> usePlaintext(Boolean.parseBoolean(value)),
        PLAINTEXT_CONNECTION_VAR,
        ZeebeClientEnvironmentVariables.PLAINTEXT_CONNECTION_VAR);
    applyEnvironmentValueIfNotNull(
        this::caCertificatePath,
        CA_CERTIFICATE_VAR,
        ZeebeClientEnvironmentVariables.CA_CERTIFICATE_VAR);
    applyEnvironmentValueIfNotNull(
        this::keepAlive, KEEP_ALIVE_VAR, ZeebeClientEnvironmentVariables.KEEP_ALIVE_VAR);
    applyEnvironmentValueIfNotNull(
        this::overrideAuthority,
        OVERRIDE_AUTHORITY_VAR,
        ZeebeClientEnvironmentVariables.OVERRIDE_AUTHORITY_VAR);
    if (shouldUseDefaultCredentialsProvider()) {
      credentialsProvider = createDefaultCredentialsProvider();
    }
    applyEnvironmentValueIfNotNull(
        value -> grpcAddress(getURIFromString(value)),
        GRPC_ADDRESS_VAR,
        ZeebeClientEnvironmentVariables.GRPC_ADDRESS_VAR);
    applyEnvironmentValueIfNotNull(
        value -> restAddress(getURIFromString(value)),
        REST_ADDRESS_VAR,
        ZeebeClientEnvironmentVariables.REST_ADDRESS_VAR);
    applyEnvironmentValueIfNotNull(
        value -> preferRestOverGrpc(Boolean.parseBoolean(value)),
        PREFER_REST_VAR,
        ZeebeClientEnvironmentVariables.PREFER_REST_VAR);
    applyEnvironmentValueIfNotNull(
        this::defaultTenantId,
        DEFAULT_TENANT_ID_VAR,
        ZeebeClientEnvironmentVariables.DEFAULT_TENANT_ID_VAR);
    applyEnvironmentValueIfNotNull(
        value -> defaultJobWorkerTenantIds(Arrays.asList(value.split(TENANT_ID_LIST_SEPARATOR))),
        DEFAULT_JOB_WORKER_TENANT_IDS_VAR,
        ZeebeClientEnvironmentVariables.DEFAULT_JOB_WORKER_TENANT_IDS_VAR);
    applyEnvironmentValueIfNotNull(
        value -> defaultJobWorkerStreamEnabled(Boolean.parseBoolean(value)),
        CAMUNDA_CLIENT_WORKER_STREAM_ENABLED,
        ZeebeClientEnvironmentVariables.ZEEBE_CLIENT_WORKER_STREAM_ENABLED);
    applyEnvironmentValueIfNotNull(
        value -> useDefaultRetryPolicy(Boolean.parseBoolean(value)),
        USE_DEFAULT_RETRY_POLICY_VAR,
        ZeebeClientEnvironmentVariables.USE_DEFAULT_RETRY_POLICY_VAR);
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
    BuilderUtils.appendProperty(sb, "defaultRequestTimeoutOffset", defaultRequestTimeoutOffset);
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
        && (Environment.system().isDefined(OAUTH_ENV_CLIENT_ID)
            || Environment.system().isDefined(ZeebeClientEnvironmentVariables.OAUTH_ENV_CLIENT_ID))
        && (Environment.system().isDefined(OAUTH_ENV_CLIENT_SECRET)
            || Environment.system()
                .isDefined(ZeebeClientEnvironmentVariables.OAUTH_ENV_CLIENT_SECRET));
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

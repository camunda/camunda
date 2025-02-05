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
package io.camunda.zeebe.spring.client.properties;

import io.camunda.client.CamundaClientConfiguration;
import io.camunda.client.impl.CamundaClientBuilderImpl;
import io.camunda.client.impl.util.Environment;
import io.camunda.zeebe.spring.client.annotation.value.ZeebeWorkerValue;
import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.DeprecatedConfigurationProperty;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@ConfigurationProperties(prefix = "zeebe.client")
@Deprecated
public class ZeebeClientConfigurationProperties {
  // Used to read default config values
  public static final CamundaClientBuilderImpl DEFAULT =
      (CamundaClientBuilderImpl) new CamundaClientBuilderImpl().withProperties(new Properties());
  public static final String CONNECTION_MODE_CLOUD = "CLOUD";
  public static final String CONNECTION_MODE_ADDRESS = "ADDRESS";
  private static final Logger LOGGER =
      LoggerFactory.getLogger(ZeebeClientConfigurationProperties.class);
  private final org.springframework.core.env.Environment environment;

  /**
   * Connection mode can be set to "CLOUD" (connect to SaaS with properties), or "ADDRESS" (to use a
   * manually set address to the broker) If not set, "CLOUD" is used if a
   * `zeebe.client.cloud.cluster-id` property is set, "ADDRESS" otherwise.
   */
  private String connectionMode;

  private String defaultTenantId = DEFAULT.getDefaultTenantId();

  private List<String> defaultJobWorkerTenantIds;

  private boolean applyEnvironmentVariableOverrides =
      false; // the default is NOT to overwrite anything by environment variables in a Spring Boot
  // world - it is unintuitive

  private boolean enabled = true;

  @NestedConfigurationProperty private Broker broker = new Broker();

  @NestedConfigurationProperty private Cloud cloud = new Cloud();

  @NestedConfigurationProperty private Worker worker = new Worker();

  @NestedConfigurationProperty private Message message = new Message();

  @NestedConfigurationProperty private Security security = new Security();

  @NestedConfigurationProperty private Job job = new Job();

  private boolean ownsJobWorkerExecutor;

  private boolean defaultJobWorkerStreamEnabled = DEFAULT.getDefaultJobWorkerStreamEnabled();
  private Duration requestTimeout = DEFAULT.getDefaultRequestTimeout();

  @Autowired
  public ZeebeClientConfigurationProperties(
      final org.springframework.core.env.Environment environment) {
    this.environment = environment;
  }

  /**
   * Make sure environment variables and other legacy config options are taken into account.
   * Environment variables are taking precedence over Spring properties. Legacy config options are
   * read only if no real property is set
   */
  @PostConstruct
  @Deprecated
  @DeprecatedConfigurationProperty(replacement = "no overrides are applied anymore")
  public void applyOverrides() {
    if (isApplyEnvironmentVariableOverrides()) {
      if (Environment.system().isDefined("ZEEBE_INSECURE_CONNECTION")) {
        security.plaintext = Environment.system().getBoolean("ZEEBE_INSECURE_CONNECTION");
      }
      if (Environment.system().isDefined("ZEEBE_CA_CERTIFICATE_PATH")) {
        security.certPath = Environment.system().get("ZEEBE_CA_CERTIFICATE_PATH");
      }
      if (Environment.system().isDefined("ZEEBE_KEEP_ALIVE")) {
        broker.keepAlive =
            Duration.ofMillis(Long.parseUnsignedLong(Environment.system().get("ZEEBE_KEEP_ALIVE")));
      }
      if (Environment.system().isDefined("ZEEBE_OVERRIDE_AUTHORITY")) {
        security.overrideAuthority = Environment.system().get("ZEEBE_OVERRIDE_AUTHORITY");
      }
    }

    if (environment != null) {
      // Environment==null can happen in test cases where the environment is not set
      // Java Client has some name differences in properties - support those as well in case people
      // use those (https://github.com/camunda-community-hub/spring-zeebe/issues/350)
      if (broker.gatewayAddress == null
          && environment.containsProperty(
              io.camunda.zeebe.client.ClientProperties.GATEWAY_ADDRESS)) {
        broker.gatewayAddress =
            environment.getProperty(io.camunda.zeebe.client.ClientProperties.GATEWAY_ADDRESS);
      }
      if (cloud.clientSecret == null
          && environment.containsProperty(
              io.camunda.zeebe.client.ClientProperties.CLOUD_CLIENT_SECRET)) {
        cloud.clientSecret =
            environment.getProperty(io.camunda.zeebe.client.ClientProperties.CLOUD_CLIENT_SECRET);
      }
      if (worker.defaultName == null
          && environment.containsProperty(
              io.camunda.zeebe.client.ClientProperties.DEFAULT_JOB_WORKER_NAME)) {
        worker.defaultName =
            environment.getProperty(
                io.camunda.zeebe.client.ClientProperties.DEFAULT_JOB_WORKER_NAME);
      }
      // Support environment based default tenant id override if value is client default fallback
      if ((defaultTenantId == null || defaultTenantId.equals(DEFAULT.getDefaultTenantId()))
          && environment.containsProperty(
              io.camunda.zeebe.client.ClientProperties.DEFAULT_TENANT_ID)) {
        defaultTenantId =
            environment.getProperty(io.camunda.zeebe.client.ClientProperties.DEFAULT_TENANT_ID);
      }
    }

    // Support default job worker tenant ids based on the default tenant id
    if (defaultJobWorkerTenantIds == null && defaultTenantId != null) {
      defaultJobWorkerTenantIds = Collections.singletonList(defaultTenantId);
    }
  }

  @Deprecated
  @DeprecatedConfigurationProperty(replacement = "nested property is deprecated")
  public Broker getBroker() {
    return broker;
  }

  public void setBroker(final Broker broker) {
    this.broker = broker;
  }

  @Deprecated
  @DeprecatedConfigurationProperty(replacement = "nested property is deprecated")
  public Cloud getCloud() {
    return cloud;
  }

  public void setCloud(final Cloud cloud) {
    this.cloud = cloud;
  }

  @Deprecated
  @DeprecatedConfigurationProperty(replacement = "nested property is deprecated")
  public Worker getWorker() {
    return worker;
  }

  public void setWorker(final Worker worker) {
    this.worker = worker;
  }

  @Deprecated
  @DeprecatedConfigurationProperty(replacement = "nested property is deprecated")
  public Message getMessage() {
    return message;
  }

  public void setMessage(final Message message) {
    this.message = message;
  }

  @Deprecated
  @DeprecatedConfigurationProperty(replacement = "nested property is deprecated")
  public Security getSecurity() {
    return security;
  }

  public void setSecurity(final Security security) {
    this.security = security;
  }

  @Deprecated
  @DeprecatedConfigurationProperty(replacement = "nested property is deprecated")
  public Job getJob() {
    return job;
  }

  public void setJob(final Job job) {
    this.job = job;
  }

  @Deprecated
  @DeprecatedConfigurationProperty(replacement = "camunda.client.zeebe.request-timeout")
  public Duration getRequestTimeout() {
    return requestTimeout;
  }

  public void setRequestTimeout(final Duration requestTimeout) {
    this.requestTimeout = requestTimeout;
  }

  @Deprecated
  @DeprecatedConfigurationProperty(replacement = "camunda.client.zeebe.enabled")
  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

  @Deprecated
  @DeprecatedConfigurationProperty(replacement = "not required")
  public boolean isApplyEnvironmentVariableOverrides() {
    return applyEnvironmentVariableOverrides;
  }

  public void setApplyEnvironmentVariableOverrides(
      final boolean applyEnvironmentVariableOverrides) {
    this.applyEnvironmentVariableOverrides = applyEnvironmentVariableOverrides;
  }

  public void setOwnsJobWorkerExecutor(final boolean ownsJobWorkerExecutor) {
    this.ownsJobWorkerExecutor = ownsJobWorkerExecutor;
  }

  @Deprecated
  @DeprecatedConfigurationProperty(
      replacement = "always true, unless custom zeebe client executor service is registered")
  public boolean ownsJobWorkerExecutor() {
    return ownsJobWorkerExecutor;
  }

  /**
   * @deprecated since 8.5 for removal with 8.8, replaced by {@link
   *     ZeebeClientConfigurationProperties#getGrpcAddress()}
   * @see CamundaClientConfiguration#getGatewayAddress()
   */
  @Deprecated
  @DeprecatedConfigurationProperty(replacement = "camunda.client.zeebe.grpc-address")
  public String getGatewayAddress() {
    if (connectionMode != null && !connectionMode.isEmpty()) {
      LOGGER.info("Using connection mode '{}' to connect to Zeebe", connectionMode);
      if (CONNECTION_MODE_CLOUD.equalsIgnoreCase(connectionMode)) {
        return cloud.getGatewayAddress();
      } else if (CONNECTION_MODE_ADDRESS.equalsIgnoreCase(connectionMode)) {
        return broker.getGatewayAddress();
      } else {
        throw new RuntimeException(createInvalidConnectionModeErrorMessage());
      }
    } else if (cloud.isConfigured()) {
      return cloud.getGatewayAddress();
    } else {
      return broker.getGatewayAddress();
    }
  }

  @Deprecated
  @DeprecatedConfigurationProperty(replacement = "camunda.client.zeebe.grpc-address")
  public URI getGrpcAddress() {
    if (connectionMode != null && !connectionMode.isEmpty()) {
      LOGGER.info("Using connection mode '{}' to connect to Zeebe GRPC", connectionMode);
      if (CONNECTION_MODE_CLOUD.equalsIgnoreCase(connectionMode)) {
        return cloud.getGrpcAddress();
      } else if (CONNECTION_MODE_ADDRESS.equalsIgnoreCase(connectionMode)) {
        return broker.getGrpcAddress();
      } else {
        throw new RuntimeException(createInvalidConnectionModeErrorMessage());
      }
    } else if (cloud.isConfigured()) {
      return cloud.getGrpcAddress();
    } else {
      return broker.getGrpcAddress();
    }
  }

  @Deprecated
  @DeprecatedConfigurationProperty(replacement = "camunda.client.zeebe.rest-address")
  public URI getRestAddress() {
    if (connectionMode != null && !connectionMode.isEmpty()) {
      LOGGER.info("Using connection mode '{}' to connect to Zeebe REST", connectionMode);
      if (CONNECTION_MODE_CLOUD.equalsIgnoreCase(connectionMode)) {
        return cloud.getRestAddress();
      } else if (CONNECTION_MODE_ADDRESS.equalsIgnoreCase(connectionMode)) {
        return broker.getRestAddress();
      } else {
        throw new RuntimeException(createInvalidConnectionModeErrorMessage());
      }
    } else if (cloud.isConfigured()) {
      return cloud.getRestAddress();
    } else {
      return broker.getRestAddress();
    }
  }

  @Deprecated
  @DeprecatedConfigurationProperty(
      replacement = "camunda.client.tenant-ids",
      reason = "the first provided tenant id is applied")
  public String getDefaultTenantId() {
    return defaultTenantId;
  }

  public void setDefaultTenantId(final String defaultTenantId) {
    this.defaultTenantId = defaultTenantId;
  }

  @Deprecated
  @DeprecatedConfigurationProperty(replacement = "camunda.client.tenant-ids")
  public List<String> getDefaultJobWorkerTenantIds() {
    return defaultJobWorkerTenantIds;
  }

  public void setDefaultJobWorkerTenantIds(final List<String> defaultJobWorkerTenantIds) {
    this.defaultJobWorkerTenantIds = defaultJobWorkerTenantIds;
  }

  @Deprecated
  @DeprecatedConfigurationProperty(replacement = "camunda.client.zeebe.defaults.stream-enabled")
  public boolean getDefaultJobWorkerStreamEnabled() {
    return defaultJobWorkerStreamEnabled;
  }

  public void setDefaultJobWorkerStreamEnabled(final boolean defaultJobWorkerStreamEnabled) {
    this.defaultJobWorkerStreamEnabled = defaultJobWorkerStreamEnabled;
  }

  @Deprecated
  @DeprecatedConfigurationProperty(replacement = "not required")
  public boolean useDefaultRetryPolicy() {
    return false;
  }

  @Deprecated
  @DeprecatedConfigurationProperty(
      replacement = "camunda.client.mode",
      reason = "There are client modes now")
  public String getConnectionMode() {
    return connectionMode;
  }

  public void setConnectionMode(final String connectionMode) {
    this.connectionMode = connectionMode;
  }

  @Deprecated
  @DeprecatedConfigurationProperty(replacement = "camunda.client.zeebe.request-timeout")
  public Duration getDefaultRequestTimeout() {
    return getRequestTimeout();
  }

  @Deprecated
  @DeprecatedConfigurationProperty(replacement = "camunda.client.zeebe.execution-threads")
  public int getNumJobWorkerExecutionThreads() {
    return worker.getThreads();
  }

  @Deprecated
  @DeprecatedConfigurationProperty(replacement = "camunda.client.zeebe.defaults.max-jobs-active")
  public int getDefaultJobWorkerMaxJobsActive() {
    return worker.getMaxJobsActive();
  }

  @Deprecated
  @DeprecatedConfigurationProperty(replacement = "camunda.client.zeebe.defaults.name")
  public String getDefaultJobWorkerName() {
    return worker.getDefaultName();
  }

  @Deprecated
  @DeprecatedConfigurationProperty(replacement = "camunda.client.zeebe.defaults.type")
  public String getDefaultJobWorkerType() {
    return worker.getDefaultType();
  }

  @Deprecated
  @DeprecatedConfigurationProperty(replacement = "camunda.client.zeebe.defaults.timeout")
  public Duration getDefaultJobTimeout() {
    return job.getTimeout();
  }

  @Deprecated
  @DeprecatedConfigurationProperty(replacement = "camunda.client.zeebe.defaults.poll-interval")
  public Duration getDefaultJobPollInterval() {
    return job.getPollInterval();
  }

  @Deprecated
  @DeprecatedConfigurationProperty(replacement = "camunda.client.zeebe.message-time-to-live")
  public Duration getDefaultMessageTimeToLive() {
    return message.getTimeToLive();
  }

  @Deprecated
  @DeprecatedConfigurationProperty(
      replacement = "camunda.client.zeebe.grpc-address",
      reason = "plaintext is determined by the url protocol (http/https) now")
  public Boolean isPlaintextConnectionEnabled() {
    return security.isPlaintext();
  }

  @Deprecated
  @DeprecatedConfigurationProperty(replacement = "camunda.client.zeebe.ca-certificate-path")
  public String getCaCertificatePath() {
    return security.getCertPath();
  }

  @Deprecated
  @DeprecatedConfigurationProperty(replacement = "camunda.client.zeebe.override-authority")
  public String getOverrideAuthority() {
    return security.getOverrideAuthority();
  }

  @Deprecated
  @DeprecatedConfigurationProperty(replacement = "camunda.client.zeebe.keep-alive")
  public Duration getKeepAlive() {
    return broker.getKeepAlive();
  }

  @Deprecated
  @DeprecatedConfigurationProperty(replacement = "camunda.client.zeebe.max-message-size")
  public int getMaxMessageSize() {
    return message.getMaxMessageSize();
  }

  @Override
  public int hashCode() {
    return Objects.hash(broker, cloud, worker, message, security, job, requestTimeout);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ZeebeClientConfigurationProperties that = (ZeebeClientConfigurationProperties) o;
    return Objects.equals(broker, that.broker)
        && Objects.equals(cloud, that.cloud)
        && Objects.equals(worker, that.worker)
        && Objects.equals(message, that.message)
        && Objects.equals(security, that.security)
        && Objects.equals(job, that.job)
        && Objects.equals(requestTimeout, that.requestTimeout);
  }

  @Override
  public String toString() {
    return "ZeebeClientConfigurationProperties{"
        + "environment="
        + environment
        + ", connectionMode='"
        + connectionMode
        + '\''
        + ", defaultTenantId='"
        + defaultTenantId
        + '\''
        + ", defaultJobWorkerTenantIds="
        + defaultJobWorkerTenantIds
        + ", applyEnvironmentVariableOverrides="
        + applyEnvironmentVariableOverrides
        + ", enabled="
        + enabled
        + ", broker="
        + broker
        + ", cloud="
        + cloud
        + ", worker="
        + worker
        + ", message="
        + message
        + ", security="
        + security
        + ", job="
        + job
        + ", ownsJobWorkerExecutor="
        + ownsJobWorkerExecutor
        + ", defaultJobWorkerStreamEnabled="
        + defaultJobWorkerStreamEnabled
        + ", requestTimeout="
        + requestTimeout
        + '}';
  }

  private String createInvalidConnectionModeErrorMessage() {
    return "Value '"
        + connectionMode
        + "' for ConnectionMode is invalid, valid values are "
        + CONNECTION_MODE_CLOUD
        + " or "
        + CONNECTION_MODE_ADDRESS;
  }

  public static class Broker {
    /**
     * @deprecated since 8.5 for removal with 8.8, replaced by {@link Broker#getGrpcAddress()}
     * @see CamundaClientConfiguration#getGatewayAddress()
     */
    @Deprecated private String gatewayAddress;

    private URI grpcAddress;
    private URI restAddress;
    private Duration keepAlive = DEFAULT.getKeepAlive();

    /**
     * @deprecated since 8.5 for removal with 8.8, replaced by {@link Broker#getGrpcAddress()}
     * @see CamundaClientConfiguration#getGatewayAddress()
     */
    @Deprecated
    @DeprecatedConfigurationProperty(replacement = "camunda.client.zeebe.grpc-address")
    public String getGatewayAddress() {
      return gatewayAddress;
    }

    /**
     * @deprecated since 8.5 for removal with 8.8, replaced by {@link Broker#getGrpcAddress()}
     * @see CamundaClientConfiguration#getGatewayAddress()
     */
    @Deprecated
    public void setGatewayAddress(final String gatewayAddress) {
      this.gatewayAddress = gatewayAddress;
    }

    @Deprecated
    @DeprecatedConfigurationProperty(replacement = "camunda.client.zeebe.grpc-address")
    public URI getGrpcAddress() {
      return grpcAddress;
    }

    @Deprecated
    public void setGrpcAddress(final URI grpcAddress) {
      if (grpcAddress != null && grpcAddress.getHost() == null) {
        throw new IllegalArgumentException("grpcAddress must be an absolute URI");
      }
      this.grpcAddress = grpcAddress;
    }

    @Deprecated
    @DeprecatedConfigurationProperty(replacement = "camunda.client.zeebe.rest-address")
    public URI getRestAddress() {
      return restAddress;
    }

    @Deprecated
    public void setRestAddress(final URI restAddress) {
      if (restAddress != null && restAddress.getHost() == null) {
        throw new IllegalArgumentException("restAddress must be an absolute URI");
      }
      this.restAddress = restAddress;
    }

    @Deprecated
    @DeprecatedConfigurationProperty(replacement = "camunda.client.zeebe.keep-alive")
    public Duration getKeepAlive() {
      return keepAlive;
    }

    @Deprecated
    public void setKeepAlive(final Duration keepAlive) {
      this.keepAlive = keepAlive;
    }

    @Override
    public int hashCode() {
      return Objects.hash(gatewayAddress, grpcAddress, restAddress, keepAlive);
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final Broker broker = (Broker) o;
      return Objects.equals(gatewayAddress, broker.gatewayAddress)
          && Objects.equals(grpcAddress, broker.grpcAddress)
          && Objects.equals(restAddress, broker.restAddress)
          && Objects.equals(keepAlive, broker.keepAlive);
    }

    @Override
    public String toString() {
      return "Broker{"
          + "gatewayAddress='"
          + gatewayAddress
          + ", grpcAddress="
          + grpcAddress
          + ", restAddress="
          + restAddress
          + ", keepAlive="
          + keepAlive
          + '}';
    }
  }

  public static class Cloud {

    private String clusterId;
    private String clientId;
    private String clientSecret;
    private String region = "bru-2";
    private String scope;
    private String baseUrl = "zeebe.camunda.io";
    private String authUrl = "https://login.cloud.camunda.io/oauth/token";
    private int port = 443;
    private String credentialsCachePath;

    @Deprecated
    @DeprecatedConfigurationProperty(replacement = "camunda.client.cluster-id")
    public String getClusterId() {
      return clusterId;
    }

    public void setClusterId(final String clusterId) {
      this.clusterId = clusterId;
    }

    @Deprecated
    @DeprecatedConfigurationProperty(replacement = "camunda.client.auth.client-id")
    public String getClientId() {
      return clientId;
    }

    public void setClientId(final String clientId) {
      this.clientId = clientId;
    }

    @Deprecated
    @DeprecatedConfigurationProperty(replacement = "camunda.client.auth.client-secret")
    public String getClientSecret() {
      return clientSecret;
    }

    public void setClientSecret(final String clientSecret) {
      this.clientSecret = clientSecret;
    }

    @Deprecated
    @DeprecatedConfigurationProperty(replacement = "camunda.client.region")
    public String getRegion() {
      return region;
    }

    public void setRegion(final String region) {
      this.region = region;
    }

    @Deprecated
    @DeprecatedConfigurationProperty(replacement = "camunda.client.zeebe.scope")
    public String getScope() {
      return scope;
    }

    public void setScope(final String scope) {
      this.scope = scope;
    }

    @Deprecated
    @DeprecatedConfigurationProperty(
        replacement = "camunda.client.zeebe.grpc-address",
        reason = "The zeebe client url is now configured as http/https url")
    public String getBaseUrl() {
      return baseUrl;
    }

    public void setBaseUrl(final String baseUrl) {
      this.baseUrl = baseUrl;
    }

    @Deprecated
    @DeprecatedConfigurationProperty(replacement = "camunda.client.auth.issuer")
    public String getAuthUrl() {
      return authUrl;
    }

    public void setAuthUrl(final String authUrl) {
      this.authUrl = authUrl;
    }

    @Deprecated
    @DeprecatedConfigurationProperty(
        replacement = "camunda.client.zeebe.grpc-address",
        reason = "The zeebe client url is now configured as http/https url")
    public int getPort() {
      return port;
    }

    public void setPort(final int port) {
      this.port = port;
    }

    @Deprecated
    @DeprecatedConfigurationProperty(
        replacement = "not required",
        reason = "the identity credentials provider will not use a credentials cache file")
    public String getCredentialsCachePath() {
      return credentialsCachePath;
    }

    public void setCredentialsCachePath(final String credentialsCachePath) {
      this.credentialsCachePath = credentialsCachePath;
    }

    @Deprecated
    @DeprecatedConfigurationProperty(replacement = "camunda.client.zeebe.audience")
    public String getAudience() {
      return String.format("%s.%s.%s", clusterId, region, baseUrl);
    }

    @Deprecated
    @DeprecatedConfigurationProperty(
        replacement = "not required",
        reason = "This is determined by 'camunda.client.mode'")
    public boolean isConfigured() {
      return (clusterId != null);
    }

    /**
     * @deprecated since 8.5 for removal with 8.8, replaced by {@link Cloud#getGrpcAddress()}
     * @see CamundaClientConfiguration#getGatewayAddress()
     */
    @Deprecated
    @DeprecatedConfigurationProperty(
        replacement = "camunda.client.zeebe.grpc-address",
        reason = "The zeebe client url is now configured as http/https url")
    public String getGatewayAddress() {
      return String.format("%s.%s.%s:%d", clusterId, region, baseUrl, port);
    }

    @Deprecated
    @DeprecatedConfigurationProperty(replacement = "camunda.client.zeebe.grpc-address")
    public URI getGrpcAddress() {
      return URI.create(String.format("https://%s.%s.%s:%d", clusterId, region, baseUrl, port));
    }

    @Deprecated
    @DeprecatedConfigurationProperty(replacement = "camunda.client.zeebe.rest-address")
    public URI getRestAddress() {
      return URI.create(String.format("https://%s.%s:%d/%s", region, baseUrl, port, clusterId));
    }

    @Override
    public int hashCode() {
      return Objects.hash(
          clusterId,
          clientId,
          clientSecret,
          region,
          scope,
          baseUrl,
          authUrl,
          port,
          credentialsCachePath);
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final Cloud cloud = (Cloud) o;
      return port == cloud.port
          && Objects.equals(clusterId, cloud.clusterId)
          && Objects.equals(clientId, cloud.clientId)
          && Objects.equals(clientSecret, cloud.clientSecret)
          && Objects.equals(region, cloud.region)
          && Objects.equals(scope, cloud.scope)
          && Objects.equals(baseUrl, cloud.baseUrl)
          && Objects.equals(authUrl, cloud.authUrl)
          && Objects.equals(credentialsCachePath, cloud.credentialsCachePath);
    }

    @Override
    public String toString() {
      return "Cloud{"
          + "clusterId='"
          + clusterId
          + '\''
          + ", clientId='"
          + "***"
          + '\''
          + ", clientSecret='"
          + "***"
          + '\''
          + ", region='"
          + region
          + '\''
          + ", scope='"
          + scope
          + '\''
          + ", baseUrl='"
          + baseUrl
          + '\''
          + ", authUrl='"
          + authUrl
          + '\''
          + ", port="
          + port
          + ", credentialsCachePath='"
          + credentialsCachePath
          + '\''
          + '}';
    }
  }

  public static class Worker {
    private Integer maxJobsActive = DEFAULT.getDefaultJobWorkerMaxJobsActive();
    private Integer threads = DEFAULT.getNumJobWorkerExecutionThreads();
    private String defaultName =
        null; // setting NO default in Spring, as bean/method name is used as default
    private String defaultType = null;
    private Map<String, ZeebeWorkerValue> override = new HashMap<>();

    @Deprecated
    @DeprecatedConfigurationProperty(replacement = "camunda.client.zeebe.override")
    public Map<String, ZeebeWorkerValue> getOverride() {
      return override;
    }

    public void setOverride(final Map<String, ZeebeWorkerValue> override) {
      this.override = override;
    }

    @Deprecated
    @DeprecatedConfigurationProperty(replacement = "camunda.client.zeebe.defaults.max-jobs-active")
    public Integer getMaxJobsActive() {
      return maxJobsActive;
    }

    public void setMaxJobsActive(final Integer maxJobsActive) {
      this.maxJobsActive = maxJobsActive;
    }

    @Deprecated
    @DeprecatedConfigurationProperty(replacement = "camunda.client.zeebe.execution-threads")
    public Integer getThreads() {
      return threads;
    }

    public void setThreads(final Integer threads) {
      this.threads = threads;
    }

    @Deprecated
    @DeprecatedConfigurationProperty(replacement = "camunda.client.zeebe.defaults.name")
    public String getDefaultName() {
      return defaultName;
    }

    public void setDefaultName(final String defaultName) {
      this.defaultName = defaultName;
    }

    @Deprecated
    @DeprecatedConfigurationProperty(replacement = "camunda.client.zeebe.defaults.type")
    public String getDefaultType() {
      return defaultType;
    }

    public void setDefaultType(final String defaultType) {
      this.defaultType = defaultType;
    }

    @Override
    public int hashCode() {
      return Objects.hash(maxJobsActive, threads, defaultName, defaultType, override);
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final Worker worker = (Worker) o;
      return Objects.equals(maxJobsActive, worker.maxJobsActive)
          && Objects.equals(threads, worker.threads)
          && Objects.equals(defaultName, worker.defaultName)
          && Objects.equals(defaultType, worker.defaultType)
          && Objects.equals(override, worker.override);
    }

    @Override
    public String toString() {
      return "Worker{"
          + "maxJobsActive="
          + maxJobsActive
          + ", threads="
          + threads
          + ", defaultName='"
          + defaultName
          + '\''
          + ", defaultType='"
          + defaultType
          + '\''
          + ", override="
          + override
          + '}';
    }
  }

  public static class Job {

    private Duration timeout = DEFAULT.getDefaultJobTimeout();
    private Duration pollInterval = DEFAULT.getDefaultJobPollInterval();

    @Deprecated
    @DeprecatedConfigurationProperty(replacement = "camunda.client.zeebe.defaults.timeout")
    public Duration getTimeout() {
      return timeout;
    }

    public void setTimeout(final Duration timeout) {
      this.timeout = timeout;
    }

    @Deprecated
    @DeprecatedConfigurationProperty(replacement = "camunda.client.zeebe.defaults.poll-interval")
    public Duration getPollInterval() {
      return pollInterval;
    }

    public void setPollInterval(final Duration pollInterval) {
      this.pollInterval = pollInterval;
    }

    @Override
    public int hashCode() {
      return Objects.hash(timeout, pollInterval);
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final Job job = (Job) o;
      return Objects.equals(timeout, job.timeout) && Objects.equals(pollInterval, job.pollInterval);
    }

    @Override
    public String toString() {
      return "Job{" + "timeout=" + timeout + ", pollInterval=" + pollInterval + '}';
    }
  }

  public static class Message {

    private Duration timeToLive = DEFAULT.getDefaultMessageTimeToLive();
    private int maxMessageSize = DEFAULT.getMaxMessageSize();

    @Deprecated
    @DeprecatedConfigurationProperty(replacement = "camunda.client.zeebe.message-time-to-live")
    public Duration getTimeToLive() {
      return timeToLive;
    }

    public void setTimeToLive(final Duration timeToLive) {
      this.timeToLive = timeToLive;
    }

    @Deprecated
    @DeprecatedConfigurationProperty(replacement = "camunda.client.zeebe.max-message-size")
    public int getMaxMessageSize() {
      return maxMessageSize;
    }

    public void setMaxMessageSize(final int maxMessageSize) {
      this.maxMessageSize = maxMessageSize;
    }

    @Override
    public int hashCode() {
      return Objects.hash(timeToLive);
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final Message message = (Message) o;
      return Objects.equals(timeToLive, message.timeToLive);
    }

    @Override
    public String toString() {
      return "Message{" + "timeToLive=" + timeToLive + ", maxMessageSize=" + maxMessageSize + '}';
    }
  }

  public static class Security {

    private Boolean plaintext;
    private String overrideAuthority = DEFAULT.getOverrideAuthority();
    private String certPath = DEFAULT.getCaCertificatePath();

    @Deprecated
    @DeprecatedConfigurationProperty(
        replacement = "camunda.client.zeebe.grpc-address",
        reason = "plaintext is determined by the url protocol (http/https) now")
    public Boolean isPlaintext() {
      return plaintext;
    }

    public void setPlaintext(final Boolean plaintext) {
      this.plaintext = plaintext;
    }

    @Deprecated
    @DeprecatedConfigurationProperty(replacement = "camunda.client.zeebe.ca-certificate-path")
    public String getCertPath() {
      return certPath;
    }

    public void setCertPath(final String certPath) {
      this.certPath = certPath;
    }

    @Deprecated
    @DeprecatedConfigurationProperty(replacement = "camunda.client.zeebe.override-authority")
    public String getOverrideAuthority() {
      return overrideAuthority;
    }

    public void setOverrideAuthority(final String overrideAuthority) {
      this.overrideAuthority = overrideAuthority;
    }

    @Override
    public int hashCode() {
      return Objects.hash(plaintext, overrideAuthority, certPath);
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final Security security = (Security) o;
      return plaintext == security.plaintext
          && Objects.equals(overrideAuthority, security.overrideAuthority)
          && Objects.equals(certPath, security.certPath);
    }

    @Override
    public String toString() {
      return "Security{"
          + "plaintext="
          + plaintext
          + ", overrideAuthority='"
          + overrideAuthority
          + '\''
          + ", certPath='"
          + certPath
          + '\''
          + '}';
    }
  }
}

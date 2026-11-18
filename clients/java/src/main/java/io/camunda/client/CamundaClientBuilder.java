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
package io.camunda.client;

import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.command.CommandWithTenantStep;
import io.camunda.client.api.command.enums.TenantFilter;
import io.camunda.client.api.worker.JobExceptionHandler;
import io.camunda.client.api.worker.JobHandler;
import io.camunda.client.api.worker.JobWorkerBuilderStep1.JobWorkerBuilderStep3;
import io.grpc.ClientInterceptor;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import org.apache.hc.client5.http.async.AsyncExecChainHandler;

/** A builder to create a {@link CamundaClient}. */
public interface CamundaClientBuilder {

  /**
   * Sets all the properties from a {@link Properties} object. Can be used to configure the client
   * from a properties file.
   *
   * <p>See {@link ClientProperties} for valid property names.
   */
  CamundaClientBuilder withProperties(Properties properties);

  /**
   * Sets the configuration from a {@link CamundaClientConfiguration} object. Can be used to
   * configure a client from a base configuration.
   */
  CamundaClientBuilder withConfiguration(CamundaClientConfiguration configuration);

  /**
   * Allows to disable the mechanism to override some properties by ENVIRONMENT VARIABLES. This is
   * useful if a client shall be constructed for test cases or in an environment that wants to fully
   * control properties (like Spring Boot).
   *
   * <p>The default value is <code>true</code>.
   */
  CamundaClientBuilder applyEnvironmentVariableOverrides(
      final boolean applyEnvironmentVariableOverrides);

  /**
   * @param restAddress the REST API address of a gateway that the client can connect to. The
   *     address must be an absolute URL, including the scheme.
   *     <p>The default value is {@code https://0.0.0.0:8080}.
   */
  CamundaClientBuilder restAddress(URI restAddress);

  /**
   * @param grpcAddress the gRPC address of a gateway that the client can connect to. The address
   *     must be an absolute URL, including the scheme.
   *     <p>The default value is {@code https://0.0.0.0:26500}.
   */
  CamundaClientBuilder grpcAddress(URI grpcAddress);

  /**
   * @param tenantId the tenant identifier which is used for tenant-aware commands when no tenant
   *     identifier is set. The default value is {@link
   *     CommandWithTenantStep#DEFAULT_TENANT_IDENTIFIER}.
   */
  CamundaClientBuilder defaultTenantId(String tenantId);

  /**
   * @param physicalTenantId the physical tenant identifier sent as the {@code
   *     Camunda-Physical-Tenant} gRPC metadata header on every outgoing call. When {@code null} the
   *     header is omitted and the gateway applies its own default.
   */
  CamundaClientBuilder physicalTenantId(String physicalTenantId);

  /**
   * Controls whether the client prefixes the REST base path with {@code /physical-tenants/<id>}
   * when a {@link #physicalTenantId(String) physical tenant id} is set (default {@code true}). Set
   * to {@code false} to use the configured REST address verbatim — e.g. when a reverse proxy or a
   * custom path layout already routes to the physical tenant. Has no effect on the gRPC {@code
   * Camunda-Physical-Tenant} header, which is always sent when a physical tenant id is set.
   *
   * <p>Cluster-scoped endpoints such as the {@link CamundaClient#newStatusRequest() cluster status}
   * report on the whole cluster and are served outside the per-tenant path, so a proxy fronting a
   * physical tenant must pass them through unprefixed.
   *
   * @param prefixPhysicalTenantPath whether to auto-prefix the REST base path per physical tenant
   */
  CamundaClientBuilder prefixPhysicalTenantPath(boolean prefixPhysicalTenantPath);

  /**
   * @param tenantIds the tenant identifiers which are used for job-activation commands when no
   *     tenant identifiers are set. The default value contains only {@link
   *     CommandWithTenantStep#DEFAULT_TENANT_IDENTIFIER}.
   */
  CamundaClientBuilder defaultJobWorkerTenantIds(List<String> tenantIds);

  /**
   * The behavior to adopt when filtering jobs during activation by a given worker. See {@link
   * TenantFilter} for possible values. The default value is {@link TenantFilter#PROVIDED}.
   *
   * @param TenantFilter the default filter to use for all workers
   */
  CamundaClientBuilder defaultJobWorkerTenantFilter(TenantFilter tenantFilter);

  /**
   * @param maxJobsActive Default value for {@link JobWorkerBuilderStep3#maxJobsActive(int)}.
   *     Default value is 32.
   */
  CamundaClientBuilder defaultJobWorkerMaxJobsActive(int maxJobsActive);

  /**
   * @param numThreads The number of threads for invocation of job workers. Default value is 1.
   *     Setting this value to 0 causes the client to reuse the scheduled executor for job handling
   *     (backward compatibility behavior).
   */
  CamundaClientBuilder numJobWorkerExecutionThreads(int numThreads);

  /**
   * Identical behavior as {@link #jobWorkerExecutor(ScheduledExecutorService,boolean)}, replaced in
   * favor of more fine-grained configuration of job worker executors.
   *
   * @deprecated use {@link #jobWorkerSchedulingExecutor(ScheduledExecutorService, boolean)} and
   *     {@link #jobHandlingExecutor(ExecutorService, boolean)} instead
   */
  @Deprecated
  default CamundaClientBuilder jobWorkerExecutor(final ScheduledExecutorService executor) {
    return jobWorkerExecutor(executor, true);
  }

  /**
   * Identical behavior as {@link #jobWorkerSchedulingExecutor(ScheduledExecutorService,boolean)}.
   * Replaced in favor of more fine-grained configuration of job worker executors.
   *
   * @deprecated use {@link #jobWorkerSchedulingExecutor(ScheduledExecutorService, boolean)} and
   *     {@link #jobHandlingExecutor(ExecutorService, boolean)} instead
   */
  @Deprecated
  CamundaClientBuilder jobWorkerExecutor(
      final ScheduledExecutorService executor, final boolean takeOwnership);

  /**
   * Identical behavior as {@link #jobWorkerSchedulingExecutor(ScheduledExecutorService,boolean)},
   * but taking ownership of the executor by default. This means the given executor is closed when
   * the client is closed.
   *
   * @param executor an executor service to use for scheduling job worker tasks
   * @see #jobWorkerSchedulingExecutor(ScheduledExecutorService, boolean)
   */
  default CamundaClientBuilder jobWorkerSchedulingExecutor(
      final ScheduledExecutorService executor) {
    return jobWorkerSchedulingExecutor(executor, true);
  }

  /**
   * Allows passing a custom scheduled executor service that will be used for scheduling job worker
   * tasks such as polling for new jobs.
   *
   * <p>Note that job handling (i.e. executing the {@link JobHandler}) is done on a separate
   * executor configured via {@link #jobHandlingExecutor(ExecutorService, boolean)}.
   *
   * <p>If no job handling executor is provided, this scheduling executor will also be used for job
   * handling. If no executor is provided here, a default scheduled executor will be created.
   *
   * @param executor an executor service to use for scheduling job worker tasks
   * @param takeOwnership if true, the executor will be closed when the client is closed otherwise,
   *     it's up to the caller to manage its lifecycle
   * @see #jobHandlingExecutor(ExecutorService, boolean)
   */
  CamundaClientBuilder jobWorkerSchedulingExecutor(
      final ScheduledExecutorService executor, final boolean takeOwnership);

  /**
   * Identical behavior as {@link #jobHandlingExecutor(ExecutorService,boolean)}, but taking
   * ownership of the executor by default. This means the given executor is closed when the client
   * is closed.
   *
   * @param executor an executor service to use for handling jobs
   * @see #jobHandlingExecutor(ExecutorService, boolean)
   */
  default CamundaClientBuilder jobHandlingExecutor(final ExecutorService executor) {
    return jobHandlingExecutor(executor, true);
  }

  /**
   * Allows passing a custom executor service that will be used for handling jobs (i.e. executing
   * the {@link JobHandler}). This executor is separate from the scheduling executor configured via
   * {@link #jobWorkerSchedulingExecutor(ScheduledExecutorService, boolean)}.
   *
   * <p>If no executor is provided here, the scheduling executor will also be used for job handling.
   *
   * <p>When non-null, this setting override {@link #numJobWorkerExecutionThreads(int)}.
   *
   * @param executor an executor service to use for handling jobs
   * @param takeOwnership if true, the executor will be closed when the client is closed otherwise,
   *     it's up to the caller to manage its lifecycle
   */
  CamundaClientBuilder jobHandlingExecutor(
      final ExecutorService executor, final boolean takeOwnership);

  /**
   * The name of the worker which is used when none is set for a job worker. Default is 'default'.
   */
  CamundaClientBuilder defaultJobWorkerName(String workerName);

  /** The timeout which is used when none is provided for a job worker. Default is 5 minutes. */
  CamundaClientBuilder defaultJobTimeout(Duration timeout);

  /**
   * The interval which a job worker is periodically polling for new jobs. Default is 100
   * milliseconds.
   */
  CamundaClientBuilder defaultJobPollInterval(Duration pollInterval);

  /** The time-to-live which is used when none is provided for a message. Default is 1 hour. */
  CamundaClientBuilder defaultMessageTimeToLive(Duration timeToLive);

  /** The request timeout used if not overridden by the command. Default is 10 seconds. */
  CamundaClientBuilder defaultRequestTimeout(Duration requestTimeout);

  /**
   * The request timeout client offset is used in commands where the {@link
   * #defaultRequestTimeout(Duration)} is also passed to the server. This ensures that the client
   * timeout does not occur before the server timeout.
   *
   * <p>The client-side timeout for these commands is calculated as the sum of {@code
   * defaultRequestTimeout} and {@code defaultRequestTimeoutOffset}.
   *
   * <p>Default is 1 second.
   */
  CamundaClientBuilder defaultRequestTimeoutOffset(Duration requestTimeoutOffset);

  /**
   * Path to a root CA certificate to be used instead of the certificate in the default default
   * store.
   */
  CamundaClientBuilder caCertificatePath(String certificatePath);

  /**
   * Configures the client certificate used for mutual TLS (mTLS) against the cluster, for both the
   * gRPC and the REST connection. Both the certificate chain and the private key must be PEM
   * encoded; the key may be PKCS#8 (encrypted or unencrypted), PKCS#1 ({@code BEGIN RSA PRIVATE
   * KEY}), or an EC key.
   *
   * <p>Environment variables: {@code CAMUNDA_MTLS_CERT_PATH} and {@code CAMUNDA_MTLS_KEY_PATH}.
   *
   * <p>This configures the cluster transport only; it is independent of the OAuth token endpoint
   * keystore ({@code CAMUNDA_SSL_CLIENT_KEYSTORE_*}). Mutually exclusive with {@link
   * #clientKeystore(String, String, String)}.
   *
   * @param certificateChainPath path to the PEM file with the client certificate chain
   * @param privateKeyPath path to the PEM file with the client private key
   */
  CamundaClientBuilder clientCertificate(String certificateChainPath, String privateKeyPath);

  /**
   * Like {@link #clientCertificate(String, String)}, but takes the PEM contents inline instead of
   * file paths.
   *
   * <p>Environment variables: {@code CAMUNDA_MTLS_CERT} and {@code CAMUNDA_MTLS_KEY}.
   *
   * @param certificateChainPem the PEM encoded client certificate chain
   * @param privateKeyPem the PEM encoded client private key
   */
  CamundaClientBuilder clientCertificateContent(String certificateChainPem, String privateKeyPem);

  /**
   * The optional passphrase of an encrypted mTLS client private key.
   *
   * <p>Environment variable: {@code CAMUNDA_MTLS_KEY_PASSPHRASE}.
   */
  CamundaClientBuilder clientKeyPassphrase(String keyPassphrase);

  /**
   * Path to a PEM CA bundle used to verify the cluster's server certificate. The bundle replaces
   * the JVM default trust store for the cluster connection, so it must contain every CA required to
   * verify the cluster endpoints. Mutually exclusive with {@link #caCertificatePath(String)} and
   * {@link #mtlsTruststore(String, String)}.
   *
   * <p>Environment variable: {@code CAMUNDA_MTLS_CA_PATH}.
   */
  CamundaClientBuilder mtlsCaCertificatePath(String caPath);

  /**
   * Like {@link #mtlsCaCertificatePath(String)}, but takes the PEM content inline.
   *
   * <p>Environment variable: {@code CAMUNDA_MTLS_CA}.
   */
  CamundaClientBuilder mtlsCaCertificateContent(String caPem);

  /**
   * Configures a Java keystore (PKCS#12 or JKS, selected by file extension) holding the client
   * identity used for mutual TLS against the cluster. Mutually exclusive with {@link
   * #clientCertificate(String, String)}.
   *
   * <p>Environment variables: {@code CAMUNDA_MTLS_KEYSTORE_PATH}, {@code
   * CAMUNDA_MTLS_KEYSTORE_PASSWORD}, and {@code CAMUNDA_MTLS_KEYSTORE_KEY_PASSWORD}.
   *
   * @param keystorePath path to the keystore file
   * @param keystorePassword the keystore password (may be {@code null})
   * @param keyPassword the password of the private key entry; defaults to the keystore password
   *     when {@code null}
   */
  CamundaClientBuilder clientKeystore(
      String keystorePath, String keystorePassword, String keyPassword);

  /**
   * Configures a Java truststore (PKCS#12 or JKS, selected by file extension) with the CA
   * certificates used to verify the cluster's server certificate. Mutually exclusive with {@link
   * #caCertificatePath(String)}, {@link #mtlsCaCertificatePath(String)}, and {@link
   * #mtlsCaCertificateContent(String)}.
   *
   * <p>Environment variables: {@code CAMUNDA_MTLS_TRUSTSTORE_PATH} and {@code
   * CAMUNDA_MTLS_TRUSTSTORE_PASSWORD}.
   */
  CamundaClientBuilder mtlsTruststore(String truststorePath, String truststorePassword);

  /**
   * A custom {@link CredentialsProvider} which will be used to apply authentication credentials to
   * requests.
   */
  CamundaClientBuilder credentialsProvider(CredentialsProvider credentialsProvider);

  /** Time interval between keep alive messages sent to the gateway. The default is 45 seconds. */
  CamundaClientBuilder keepAlive(Duration keepAlive);

  /**
   * Custom implementations of the gRPC {@code ClientInterceptor} middleware API. The interceptors
   * will be applied to every gRPC call that the client makes. More details can be found at {@link
   * <a href="https://grpc.io/docs/guides/interceptors/">grpc.io</a>}.
   */
  CamundaClientBuilder withInterceptors(ClientInterceptor... interceptor);

  /**
   * Custom implementations of the Apache HttpClient {@code AsyncExecChainHandler} middleware API.
   * The middleware implementations will be called on every REST API call that the client makes.
   */
  CamundaClientBuilder withChainHandlers(AsyncExecChainHandler... chainHandler);

  CamundaClientBuilder withJsonMapper(JsonMapper jsonMapper);

  /**
   * Overrides the authority used with TLS virtual hosting. Specifically, to override hostname
   * verification in the TLS handshake. It does not change what host is actually connected to.
   *
   * <p>This method is intended for testing, but may safely be used outside of tests as an
   * alternative to DNS overrides.
   *
   * <p>This setting does nothing if a plaintext connection is used.
   *
   * @param authority The alternative authority to use, commonly in the form <code>host</code> or
   *     <code>host:port</code>
   * @apiNote For the full definition of authority see [RFC 2396: Uniform Resource Identifiers
   *     (URI): Generic Syntax](http://www.ietf.org/rfc/rfc2396.txt)
   */
  CamundaClientBuilder overrideAuthority(String authority);

  /**
   * A custom maxMessageSize allows the client to receive larger or smaller responses from Camunda.
   * Technically, it specifies the maxInboundMessageSize of the gRPC channel. The default is 5242880
   * = 5MB.
   *
   * <p>For document downloads ({@link CamundaClient#newDocumentContentGetRequest(String)}), it is
   * the size of the buffer that holds the content until the application reads it. It does not limit
   * the size of the document. If the application reads more slowly than the network delivers the
   * content, the buffer can grow larger than this, up to the size of the part of the document that
   * has not been read yet. If there is not enough memory for that, reading the document fails with
   * a {@link java.io.IOException}.
   */
  CamundaClientBuilder maxMessageSize(int maxSize);

  /**
   * A custom maxMetadataSize allows the client to receive larger or smaller response headers from
   * Camunda. Technically, it specifies the maxInboundMetadataSize of the gRPC channel. The default
   * is 16384 = 16KB .
   */
  CamundaClientBuilder maxMetadataSize(int maxSize);

  /**
   * A custom streamEnabled allows the client to use job stream instead of job poll. The default
   * value is set as enabled.
   */
  CamundaClientBuilder defaultJobWorkerStreamEnabled(boolean streamEnabled);

  /**
   * If enabled, the client will make use of the default retry policy defined. False by default.
   *
   * <p>NOTE: the default retry policy is taken from the {@code gateway-service-config.json} in the
   * {@code io.camunda:zeebe-gateway-protocol-impl} JAR.
   */
  CamundaClientBuilder useDefaultRetryPolicy(final boolean useDefaultRetryPolicy);

  /**
   * If true, will prefer to use REST over gRPC for calls which can be done over both REST and gRPC.
   * The default value is {@code true} (REST is preferred).
   *
   * <p>NOTE: job streaming is only supported via gRPC
   *
   * @param preferRestOverGrpc if true, the client will use REST instead of gRPC whenever possible
   */
  CamundaClientBuilder preferRestOverGrpc(final boolean preferRestOverGrpc);

  /**
   * If enabled, the client will use DNS-based name resolution and balance across all addresses.
   * This allows the client to distribute requests across multiple gateway instances resolved by DNS
   * without requiring an external load balancer.
   *
   * <p>Default is {@code false}.
   *
   * @param useClientSideLoadBalancing if true, enables DNS resolution and randomized load balancing
   */
  CamundaClientBuilder useClientSideLoadBalancing(final boolean useClientSideLoadBalancing);

  /** Sets the maximum number of concurrent HTTP connections the client can open. */
  CamundaClientBuilder maxHttpConnections(int maxConnections);

  /**
   * A custom retryBackoffSupplier allows the client to determine the default retry backoff strategy
   * that every job worker will apply unless configured otherwise. By default, a static retry
   * backoff of <code>PT0S</code> is used.
   *
   * @param jobExceptionHandler the retry backoff supplier to retrieve the retry backoff for every
   *     fail job command
   * @return this builder for chaining
   */
  CamundaClientBuilder defaultJobWorkerExceptionHandler(JobExceptionHandler jobExceptionHandler);

  /**
   * @return a new {@link CamundaClient} with the provided configuration options.
   */
  CamundaClient build();
}

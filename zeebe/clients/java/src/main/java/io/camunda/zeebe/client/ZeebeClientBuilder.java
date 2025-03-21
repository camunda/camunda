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
package io.camunda.zeebe.client;

import io.camunda.zeebe.client.api.ExperimentalApi;
import io.camunda.zeebe.client.api.JsonMapper;
import io.camunda.zeebe.client.api.worker.JobWorkerBuilderStep1.JobWorkerBuilderStep3;
import io.grpc.ClientInterceptor;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ScheduledExecutorService;

public interface ZeebeClientBuilder {

  /**
   * Sets all the properties from a {@link Properties} object. Can be used to configure the client
   * from a properties file.
   *
   * <p>See {@link ClientProperties} for valid property names.
   */
  ZeebeClientBuilder withProperties(Properties properties);

  /**
   * Allows to disable the mechanism to override some properties by ENVIRONMENT VARIABLES. This is
   * useful if a client shall be constructed for test cases or in an environment that wants to fully
   * control properties (like Spring Boot).
   *
   * <p>The default value is <code>true</code>.
   */
  ZeebeClientBuilder applyEnvironmentVariableOverrides(
      final boolean applyEnvironmentVariableOverrides);

  /**
   * @deprecated since 8.5 for removal with 8.8, replaced by {@link
   *     ZeebeClientBuilder#grpcAddress(URI)}
   * @param gatewayAddress the IP socket address of a gateway that the client can initially connect
   *     to. Must be in format <code>host:port</code>. The default value is <code>0.0.0.0:26500
   *     </code> .
   */
  @Deprecated
  ZeebeClientBuilder gatewayAddress(String gatewayAddress);

  /**
   * @param restAddress the REST API address of a gateway that the client can connect to. The
   *     address must be an absolute URL, including the scheme.
   *     <p>The default value is {@code https://0.0.0.0:8080}.
   */
  ZeebeClientBuilder restAddress(URI restAddress);

  /**
   * @param grpcAddress the gRPC address of a gateway that the client can connect to. The address
   *     must be an absolute URL, including the scheme.
   *     <p>The default value is {@code https://0.0.0.0:26500}.
   */
  ZeebeClientBuilder grpcAddress(URI grpcAddress);

  /**
   * @param tenantId the tenant identifier which is used for tenant-aware commands when no tenant
   *     identifier is set. The default value is {@link
   *     io.camunda.zeebe.client.api.command.CommandWithTenantStep#DEFAULT_TENANT_IDENTIFIER}.
   */
  ZeebeClientBuilder defaultTenantId(String tenantId);

  /**
   * @param tenantIds the tenant identifiers which are used for job-activation commands when no
   *     tenant identifiers are set. The default value contains only {@link
   *     io.camunda.zeebe.client.api.command.CommandWithTenantStep#DEFAULT_TENANT_IDENTIFIER}.
   */
  ZeebeClientBuilder defaultJobWorkerTenantIds(List<String> tenantIds);

  /**
   * @param maxJobsActive Default value for {@link JobWorkerBuilderStep3#maxJobsActive(int)}.
   *     Default value is 32.
   */
  ZeebeClientBuilder defaultJobWorkerMaxJobsActive(int maxJobsActive);

  /**
   * @param numThreads The number of threads for invocation of job workers. Setting this value to 0
   *     effectively disables subscriptions and workers. Default value is 1.
   */
  ZeebeClientBuilder numJobWorkerExecutionThreads(int numThreads);

  /**
   * Identical behavior as {@link #jobWorkerExecutor(ScheduledExecutorService,boolean)}, but taking
   * ownership of the executor by default. This means the given executor is closed when the client
   * is closed.
   *
   * @param executor an executor service to use when invoking job workers
   * @see #jobWorkerExecutor(ScheduledExecutorService, boolean)
   */
  default ZeebeClientBuilder jobWorkerExecutor(final ScheduledExecutorService executor) {
    return jobWorkerExecutor(executor, true);
  }

  /**
   * Allows passing a custom executor service that will be shared by all job workers created via
   * this client.
   *
   * <p>Polling and handling jobs (e.g. via {@link io.camunda.zeebe.client.api.worker.JobHandler}
   * will all be invoked on this executor.
   *
   * <p>When non-null, this setting override {@link #numJobWorkerExecutionThreads(int)}.
   *
   * @param executor an executor service to use when invoking job workers
   * @param takeOwnership if true, the executor will be closed when the client is closed. otherwise,
   *     it's up to the caller to manage its lifecycle
   */
  ZeebeClientBuilder jobWorkerExecutor(
      final ScheduledExecutorService executor, final boolean takeOwnership);

  /**
   * The name of the worker which is used when none is set for a job worker. Default is 'default'.
   */
  ZeebeClientBuilder defaultJobWorkerName(String workerName);

  /** The timeout which is used when none is provided for a job worker. Default is 5 minutes. */
  ZeebeClientBuilder defaultJobTimeout(Duration timeout);

  /**
   * The interval which a job worker is periodically polling for new jobs. Default is 100
   * milliseconds.
   */
  ZeebeClientBuilder defaultJobPollInterval(Duration pollInterval);

  /** The time-to-live which is used when none is provided for a message. Default is 1 hour. */
  ZeebeClientBuilder defaultMessageTimeToLive(Duration timeToLive);

  /** The request timeout used if not overridden by the command. Default is 10 seconds. */
  ZeebeClientBuilder defaultRequestTimeout(Duration requestTimeout);

  /** Use a plaintext connection between the client and the gateway. */
  ZeebeClientBuilder usePlaintext();

  /**
   * Path to a root CA certificate to be used instead of the certificate in the default default
   * store.
   */
  ZeebeClientBuilder caCertificatePath(String certificatePath);

  /**
   * A custom {@link CredentialsProvider} which will be used to apply authentication credentials to
   * requests.
   */
  ZeebeClientBuilder credentialsProvider(CredentialsProvider credentialsProvider);

  /** Time interval between keep alive messages sent to the gateway. The default is 45 seconds. */
  ZeebeClientBuilder keepAlive(Duration keepAlive);

  ZeebeClientBuilder withInterceptors(ClientInterceptor... interceptor);

  ZeebeClientBuilder withJsonMapper(JsonMapper jsonMapper);

  /**
   * Overrides the authority used with TLS virtual hosting. Specifically, to override hostname
   * verification in the TLS handshake. It does not change what host is actually connected to.
   *
   * <p>This method is intended for testing, but may safely be used outside of tests as an
   * alternative to DNS overrides.
   *
   * <p>This setting does nothing if a {@link #usePlaintext() plaintext} connection is used.
   *
   * @param authority The alternative authority to use, commonly in the form <code>host</code> or
   *     <code>host:port</code>
   * @apiNote For the full definition of authority see [RFC 2396: Uniform Resource Identifiers
   *     (URI): Generic Syntax](http://www.ietf.org/rfc/rfc2396.txt)
   */
  ZeebeClientBuilder overrideAuthority(String authority);

  /**
   * A custom maxMessageSize allows the client to receive larger or smaller responses from Zeebe.
   * Technically, it specifies the maxInboundMessageSize of the gRPC channel. The default is 5242880
   * = 5MB.
   */
  ZeebeClientBuilder maxMessageSize(int maxSize);

  /**
   * A custom maxMetadataSize allows the client to receive larger or smaller response headers from
   * Zeebe. Technically, it specifies the maxInboundMetadataSize of the gRPC channel. The default is
   * 16384 = 16KB .
   */
  ZeebeClientBuilder maxMetadataSize(int maxSize);

  /**
   * A custom streamEnabled allows the client to use job stream instead of job poll. The default
   * value is set as enabled.
   */
  ZeebeClientBuilder defaultJobWorkerStreamEnabled(boolean streamEnabled);

  /**
   * If enabled, the client will make use of the default retry policy defined. False by default.
   *
   * <p>NOTE: the default retry policy is taken from the {@code gateway-service-config.json} in the
   * {@code io.camunda:zeebe-gateway-protocol-impl} JAR.
   */
  ZeebeClientBuilder useDefaultRetryPolicy(final boolean useDefaultRetryPolicy);

  /**
   * If true, will prefer to use REST over gRPC for calls which can be done over both REST and gRPC.
   * This is an experimental API which is present while we migrate the bulk of the API from gRPC to
   * REST. Once done, this will also be removed.
   *
   * <p>NOTE: not all calls can be done over REST (or HTTP/1) yet, this is also subject to change.
   *
   * @param preferRestOverGrpc if true, the client will use REST instead of gRPC whenever possible
   * @deprecated since 8.5, will be removed in 8.8
   * @return this builder for chaining
   */
  @ExperimentalApi("https://github.com/camunda/zeebe/issues/16166")
  @Deprecated
  ZeebeClientBuilder preferRestOverGrpc(final boolean preferRestOverGrpc);

  /**
   * @return a new {@link ZeebeClient} with the provided configuration options.
   */
  ZeebeClient build();
}

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

import io.camunda.client.api.ExperimentalApi;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.command.CommandWithTenantStep;
import io.camunda.client.api.worker.JobHandler;
import io.camunda.client.api.worker.JobWorkerBuilderStep1.JobWorkerBuilderStep3;
import io.grpc.ClientInterceptor;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
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
   * Allows to disable the mechanism to override some properties by ENVIRONMENT VARIABLES. This is
   * useful if a client shall be constructed for test cases or in an environment that wants to fully
   * control properties (like Spring Boot).
   *
   * <p>The default value is <code>true</code>.
   */
  CamundaClientBuilder applyEnvironmentVariableOverrides(
      final boolean applyEnvironmentVariableOverrides);

  /**
   * @deprecated since 8.5 for removal with 8.8, replaced by {@link
   *     CamundaClientBuilder#grpcAddress(URI)}
   * @param gatewayAddress the IP socket address of a gateway that the client can initially connect
   *     to. Must be in format <code>host:port</code>. The default value is <code>0.0.0.0:26500
   *     </code> .
   */
  @Deprecated
  CamundaClientBuilder gatewayAddress(String gatewayAddress);

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
   * @param tenantIds the tenant identifiers which are used for job-activation commands when no
   *     tenant identifiers are set. The default value contains only {@link
   *     CommandWithTenantStep#DEFAULT_TENANT_IDENTIFIER}.
   */
  CamundaClientBuilder defaultJobWorkerTenantIds(List<String> tenantIds);

  /**
   * @param maxJobsActive Default value for {@link JobWorkerBuilderStep3#maxJobsActive(int)}.
   *     Default value is 32.
   */
  CamundaClientBuilder defaultJobWorkerMaxJobsActive(int maxJobsActive);

  /**
   * @param numThreads The number of threads for invocation of job workers. Setting this value to 0
   *     effectively disables subscriptions and workers. Default value is 1.
   */
  CamundaClientBuilder numJobWorkerExecutionThreads(int numThreads);

  /**
   * Identical behavior as {@link #jobWorkerExecutor(ScheduledExecutorService,boolean)}, but taking
   * ownership of the executor by default. This means the given executor is closed when the client
   * is closed.
   *
   * @param executor an executor service to use when invoking job workers
   * @see #jobWorkerExecutor(ScheduledExecutorService, boolean)
   */
  default CamundaClientBuilder jobWorkerExecutor(final ScheduledExecutorService executor) {
    return jobWorkerExecutor(executor, true);
  }

  /**
   * Allows passing a custom executor service that will be shared by all job workers created via
   * this client.
   *
   * <p>Polling and handling jobs (e.g. via {@link JobHandler} will all be invoked on this executor.
   *
   * <p>When non-null, this setting override {@link #numJobWorkerExecutionThreads(int)}.
   *
   * @param executor an executor service to use when invoking job workers
   * @param takeOwnership if true, the executor will be closed when the client is closed. otherwise,
   *     it's up to the caller to manage its lifecycle
   */
  CamundaClientBuilder jobWorkerExecutor(
      final ScheduledExecutorService executor, final boolean takeOwnership);

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

  /** Use a plaintext connection between the client and the gateway. */
  CamundaClientBuilder usePlaintext();

  /**
   * Path to a root CA certificate to be used instead of the certificate in the default default
   * store.
   */
  CamundaClientBuilder caCertificatePath(String certificatePath);

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
   * https://grpc.io/docs/guides/interceptors/}.
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
   * <p>This setting does nothing if a {@link #usePlaintext() plaintext} connection is used.
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
   * This is an experimental API which is present while we migrate the bulk of the API from gRPC to
   * REST. Once done, this will also be removed.
   *
   * <p>NOTE: not all calls can be done over REST (or HTTP/1) yet, this is also subject to change.
   *
   * @param preferRestOverGrpc if true, the client will use REST instead of gRPC whenever possible
   * @deprecated since 8.5, will be removed in 8.8
   * @return this builder for chaining
   */
  @ExperimentalApi("https://github.com/camunda/camunda/issues/16166")
  @Deprecated
  CamundaClientBuilder preferRestOverGrpc(final boolean preferRestOverGrpc);

  /**
   * @return a new {@link CamundaClient} with the provided configuration options.
   */
  CamundaClient build();
}

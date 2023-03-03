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

import io.camunda.zeebe.client.api.JsonMapper;
import io.camunda.zeebe.client.api.worker.JobWorkerBuilderStep1.JobWorkerBuilderStep3;
import io.grpc.ClientInterceptor;
import java.time.Duration;
import java.util.Properties;

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
   * @param gatewayAddress the IP socket address of a gateway that the client can initially connect
   *     to. Must be in format <code>host:port</code>. The default value is <code>0.0.0.0:26500
   *     </code> .
   */
  ZeebeClientBuilder gatewayAddress(String gatewayAddress);

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
   * Technically, it specifies the maxInboundMessageSize of the gRPC channel. The default is 4194304
   * = 4MB.
   */
  ZeebeClientBuilder maxMessageSize(int maxSize);

  /**
   * @return a new {@link ZeebeClient} with the provided configuration options.
   */
  ZeebeClient build();
}

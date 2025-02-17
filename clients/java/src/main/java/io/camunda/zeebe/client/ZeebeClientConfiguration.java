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
import io.grpc.ClientInterceptor;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import org.apache.hc.client5.http.async.AsyncExecChainHandler;

/**
 * @deprecated since 8.8 for removal in 8.9, replaced by {@link
 *     io.camunda.client.CamundaClientConfiguration}
 */
@Deprecated
public interface ZeebeClientConfiguration {

  /**
   * @deprecated since 8.5 for removal with 8.8, replaced by {@link
   *     ZeebeClientConfiguration#getGrpcAddress()}
   * @see ZeebeClientBuilder#grpcAddress(URI)
   */
  @Deprecated
  String getGatewayAddress();

  /**
   * @see ZeebeClientBuilder#restAddress(URI)
   */
  URI getRestAddress();

  /**
   * @see ZeebeClientBuilder#grpcAddress(URI)
   */
  URI getGrpcAddress();

  /**
   * @see ZeebeClientBuilder#defaultTenantId(String)
   */
  String getDefaultTenantId();

  /**
   * @see ZeebeClientBuilder#defaultJobWorkerTenantIds(List)
   */
  List<String> getDefaultJobWorkerTenantIds();

  /**
   * @see ZeebeClientBuilder#numJobWorkerExecutionThreads(int)
   */
  int getNumJobWorkerExecutionThreads();

  /**
   * @see ZeebeClientBuilder#defaultJobWorkerMaxJobsActive(int)
   */
  int getDefaultJobWorkerMaxJobsActive();

  /**
   * @see ZeebeClientBuilder#defaultJobWorkerName(String)
   */
  String getDefaultJobWorkerName();

  /**
   * @see ZeebeClientBuilder#defaultJobTimeout(Duration)
   */
  Duration getDefaultJobTimeout();

  /**
   * @see ZeebeClientBuilder#defaultJobPollInterval(Duration)
   */
  Duration getDefaultJobPollInterval();

  /**
   * @see ZeebeClientBuilder#defaultMessageTimeToLive(Duration)
   */
  Duration getDefaultMessageTimeToLive();

  /**
   * @see ZeebeClientBuilder#defaultRequestTimeout(Duration)
   */
  Duration getDefaultRequestTimeout();

  /**
   * @see ZeebeClientBuilder#usePlaintext()
   */
  boolean isPlaintextConnectionEnabled();

  /**
   * @see ZeebeClientBuilder#caCertificatePath(String)
   */
  String getCaCertificatePath();

  /**
   * @see ZeebeClientBuilder#credentialsProvider(CredentialsProvider)
   */
  CredentialsProvider getCredentialsProvider();

  /**
   * @see ZeebeClientBuilder#keepAlive(Duration)
   */
  Duration getKeepAlive();

  /**
   * @see ZeebeClientBuilder#withInterceptors(ClientInterceptor...)
   */
  List<ClientInterceptor> getInterceptors();

  /**
   * @see ZeebeClientBuilder#withChainHandlers(AsyncExecChainHandler...)
   */
  List<AsyncExecChainHandler> getChainHandlers();

  /**
   * @see ZeebeClientBuilder#withJsonMapper(JsonMapper)
   */
  JsonMapper getJsonMapper();

  /**
   * @see ZeebeClientBuilder#overrideAuthority(String)
   */
  String getOverrideAuthority();

  /**
   * @see ZeebeClientBuilder#maxMessageSize(int)
   */
  int getMaxMessageSize();

  /**
   * @see ZeebeClientBuilder#maxMetadataSize(int)
   */
  int getMaxMetadataSize();

  /**
   * @see ZeebeClientBuilder#jobWorkerExecutor(ScheduledExecutorService)
   */
  ScheduledExecutorService jobWorkerExecutor();

  /**
   * @see ZeebeClientBuilder#jobWorkerExecutor(ScheduledExecutorService, boolean)
   */
  boolean ownsJobWorkerExecutor();

  /**
   * @see ZeebeClientBuilder#defaultJobWorkerStreamEnabled(boolean)
   */
  boolean getDefaultJobWorkerStreamEnabled();

  /**
   * @see ZeebeClientBuilder#useDefaultRetryPolicy(boolean)
   */
  boolean useDefaultRetryPolicy();

  /**
   * @see ZeebeClientBuilder#preferRestOverGrpc(boolean)
   */
  @ExperimentalApi("https://github.com/camunda/camunda/issues/16166")
  boolean preferRestOverGrpc();
}

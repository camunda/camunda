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
import io.grpc.ClientInterceptor;
import io.opentelemetry.api.OpenTelemetry;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import org.apache.hc.client5.http.async.AsyncExecChainHandler;

public interface CamundaClientConfiguration {

  /**
   * @see CamundaClientBuilder#restAddress(URI)
   */
  URI getRestAddress();

  /**
   * @see CamundaClientBuilder#grpcAddress(URI)
   */
  URI getGrpcAddress();

  /**
   * @see CamundaClientBuilder#defaultTenantId(String)
   */
  String getDefaultTenantId();

  /**
   * @see CamundaClientBuilder#defaultJobWorkerTenantIds(List)
   */
  List<String> getDefaultJobWorkerTenantIds();

  /**
   * @see CamundaClientBuilder#numJobWorkerExecutionThreads(int)
   */
  int getNumJobWorkerExecutionThreads();

  /**
   * @see CamundaClientBuilder#defaultJobWorkerMaxJobsActive(int)
   */
  int getDefaultJobWorkerMaxJobsActive();

  /**
   * @see CamundaClientBuilder#defaultJobWorkerName(String)
   */
  String getDefaultJobWorkerName();

  /**
   * @see CamundaClientBuilder#defaultJobTimeout(Duration)
   */
  Duration getDefaultJobTimeout();

  /**
   * @see CamundaClientBuilder#defaultJobPollInterval(Duration)
   */
  Duration getDefaultJobPollInterval();

  /**
   * @see CamundaClientBuilder#defaultMessageTimeToLive(Duration)
   */
  Duration getDefaultMessageTimeToLive();

  /**
   * @see CamundaClientBuilder#defaultRequestTimeout(Duration)
   */
  Duration getDefaultRequestTimeout();

  /**
   * @see CamundaClientBuilder#defaultRequestTimeoutOffset(Duration)
   */
  Duration getDefaultRequestTimeoutOffset();

  /**
   * @see CamundaClientBuilder#caCertificatePath(String)
   */
  String getCaCertificatePath();

  /**
   * @see CamundaClientBuilder#credentialsProvider(CredentialsProvider)
   */
  CredentialsProvider getCredentialsProvider();

  /**
   * @see CamundaClientBuilder#keepAlive(Duration)
   */
  Duration getKeepAlive();

  /**
   * @see CamundaClientBuilder#withInterceptors(ClientInterceptor...)
   */
  List<ClientInterceptor> getInterceptors();

  /**
   * @see CamundaClientBuilder#withChainHandlers(AsyncExecChainHandler...)
   */
  List<AsyncExecChainHandler> getChainHandlers();

  /**
   * @see CamundaClientBuilder#withJsonMapper(JsonMapper)
   */
  JsonMapper getJsonMapper();

  /**
   * @see CamundaClientBuilder#overrideAuthority(String)
   */
  String getOverrideAuthority();

  /**
   * @see CamundaClientBuilder#maxMessageSize(int)
   */
  int getMaxMessageSize();

  /**
   * @see CamundaClientBuilder#maxMetadataSize(int)
   */
  int getMaxMetadataSize();

  /**
   * @see CamundaClientBuilder#jobWorkerExecutor(ScheduledExecutorService)
   */
  ScheduledExecutorService jobWorkerExecutor();

  /**
   * @see CamundaClientBuilder#jobWorkerExecutor(ScheduledExecutorService, boolean)
   */
  boolean ownsJobWorkerExecutor();

  /**
   * @see CamundaClientBuilder#defaultJobWorkerStreamEnabled(boolean)
   */
  boolean getDefaultJobWorkerStreamEnabled();

  /**
   * @see CamundaClientBuilder#useDefaultRetryPolicy(boolean)
   */
  boolean useDefaultRetryPolicy();

  /**
   * @see CamundaClientBuilder#preferRestOverGrpc(boolean)
   */
  @ExperimentalApi("https://github.com/camunda/camunda/issues/16166")
  boolean preferRestOverGrpc();

  OpenTelemetry getOpenTelemetry();
}

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
package io.camunda.client.spring.configuration;

import io.camunda.client.CamundaClientConfiguration;
import io.camunda.client.CredentialsProvider;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.command.enums.TenantFilter;
import io.camunda.client.api.worker.JobExceptionHandler;
import io.camunda.client.jobhandling.CamundaClientExecutorService;
import io.camunda.client.jobhandling.JobExceptionHandlerSupplier;
import io.camunda.client.jobhandling.JobExceptionHandlerSupplier.JobExceptionHandlerSupplierContext;
import io.camunda.client.spring.properties.CamundaClientProperties;
import io.grpc.ClientInterceptor;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import org.apache.hc.client5.http.async.AsyncExecChainHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SpringCamundaClientConfiguration implements CamundaClientConfiguration {

  private static final Logger LOG = LoggerFactory.getLogger(SpringCamundaClientConfiguration.class);
  private final CamundaClientProperties camundaClientProperties;
  private final JsonMapper jsonMapper;
  private final List<ClientInterceptor> interceptors;
  private final List<AsyncExecChainHandler> chainHandlers;
  private final CamundaClientExecutorService zeebeClientExecutorService;
  private final CredentialsProvider credentialsProvider;
  private final JobExceptionHandlerSupplier jobExceptionHandlerSupplier;

  public SpringCamundaClientConfiguration(
      final CamundaClientProperties camundaClientProperties,
      final JsonMapper jsonMapper,
      final List<ClientInterceptor> interceptors,
      final List<AsyncExecChainHandler> chainHandlers,
      final CamundaClientExecutorService zeebeClientExecutorService,
      final CredentialsProvider credentialsProvider,
      final JobExceptionHandlerSupplier jobExceptionHandlerSupplier) {
    this.camundaClientProperties = camundaClientProperties;
    this.jsonMapper = jsonMapper;
    this.interceptors = interceptors;
    this.chainHandlers = chainHandlers;
    this.zeebeClientExecutorService = zeebeClientExecutorService;
    this.credentialsProvider = credentialsProvider;
    this.jobExceptionHandlerSupplier = jobExceptionHandlerSupplier;
  }

  @Override
  public URI getRestAddress() {
    return camundaClientProperties.getRestAddress();
  }

  @Override
  public URI getGrpcAddress() {
    return camundaClientProperties.getGrpcAddress();
  }

  @Override
  public String getDefaultTenantId() {
    return camundaClientProperties.getTenantId();
  }

  @Override
  public List<String> getDefaultJobWorkerTenantIds() {
    return camundaClientProperties.getWorker().getDefaults().getTenantIds();
  }

  @Override
  public TenantFilter getDefaultJobWorkerTenantFilter() {
    return camundaClientProperties.getWorker().getDefaults().getTenantFilter();
  }

  @Override
  public int getNumJobWorkerExecutionThreads() {
    return camundaClientProperties.getExecutionThreads();
  }

  @Override
  public int getDefaultJobWorkerMaxJobsActive() {
    return camundaClientProperties.getWorker().getDefaults().getMaxJobsActive();
  }

  @Override
  public String getDefaultJobWorkerName() {
    return camundaClientProperties.getWorker().getDefaults().getName();
  }

  @Override
  public Duration getDefaultJobTimeout() {
    return camundaClientProperties.getWorker().getDefaults().getTimeout();
  }

  @Override
  public Duration getDefaultJobPollInterval() {
    return camundaClientProperties.getWorker().getDefaults().getPollInterval();
  }

  @Override
  public Duration getDefaultMessageTimeToLive() {
    return camundaClientProperties.getMessageTimeToLive();
  }

  @Override
  public Duration getDefaultRequestTimeout() {
    return camundaClientProperties.getRequestTimeout();
  }

  @Override
  public Duration getDefaultRequestTimeoutOffset() {
    return camundaClientProperties.getRequestTimeoutOffset();
  }

  @Override
  public String getCaCertificatePath() {
    return camundaClientProperties.getCaCertificatePath();
  }

  @Override
  public CredentialsProvider getCredentialsProvider() {
    return credentialsProvider;
  }

  @Override
  public Duration getKeepAlive() {
    return camundaClientProperties.getKeepAlive();
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
    return camundaClientProperties.getOverrideAuthority();
  }

  @Override
  public int getMaxMessageSize() {
    return Math.toIntExact(camundaClientProperties.getMaxMessageSize().toBytes());
  }

  @Override
  public int getMaxMetadataSize() {
    return Math.toIntExact(camundaClientProperties.getMaxMetadataSize().toBytes());
  }

  @Override
  public ScheduledExecutorService jobWorkerExecutor() {
    return zeebeClientExecutorService.getScheduledExecutor();
  }

  @Override
  public boolean ownsJobWorkerExecutor() {
    return zeebeClientExecutorService.isScheduledExecutorOwnedByCamundaClient();
  }

  @Override
  public ScheduledExecutorService jobWorkerSchedulingExecutor() {
    return zeebeClientExecutorService.getScheduledExecutor();
  }

  @Override
  public boolean ownsJobWorkerSchedulingExecutor() {
    return zeebeClientExecutorService.isScheduledExecutorOwnedByCamundaClient();
  }

  @Override
  public ExecutorService jobHandlingExecutor() {
    return zeebeClientExecutorService.getJobHandlingExecutor();
  }

  @Override
  public boolean ownsJobHandlingExecutor() {
    return zeebeClientExecutorService.isJobHandlingExecutorOwnedByCamundaClient();
  }

  @Override
  public boolean getDefaultJobWorkerStreamEnabled() {
    return camundaClientProperties.getWorker().getDefaults().getStreamEnabled();
  }

  @Override
  public boolean useDefaultRetryPolicy() {
    return false;
  }

  @Override
  public JobExceptionHandler getDefaultJobWorkerExceptionHandler() {
    return jobExceptionHandlerSupplier.getJobExceptionHandler(
        new JobExceptionHandlerSupplierContext(
            camundaClientProperties.getWorker().getDefaults().getRetryBackoff(),
            camundaClientProperties.getWorker().getDefaults().getMaxRetries()));
  }

  @Override
  public boolean preferRestOverGrpc() {
    return camundaClientProperties.getPreferRestOverGrpc();
  }

  @Override
  public int getMaxHttpConnections() {
    return camundaClientProperties.getMaxHttpConnections();
  }

  @Override
  public String toString() {
    return "SpringCamundaClientConfiguration{"
        + "camundaClientProperties="
        + camundaClientProperties
        + ", jsonMapper="
        + jsonMapper
        + ", interceptors="
        + interceptors
        + ", chainHandlers="
        + chainHandlers
        + ", zeebeClientExecutorService="
        + zeebeClientExecutorService
        + ", credentialsProvider="
        + (credentialsProvider == null ? "null" : credentialsProvider.getClass())
        + '}';
  }
}

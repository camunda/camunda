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

import io.camunda.client.CamundaClientCloudBuilderStep1.CamundaClientCloudBuilderStep2;
import io.camunda.client.CamundaClientCloudBuilderStep1.CamundaClientCloudBuilderStep2.CamundaClientCloudBuilderStep3;
import io.camunda.client.CamundaClientCloudBuilderStep1.CamundaClientCloudBuilderStep2.CamundaClientCloudBuilderStep3.CamundaClientCloudBuilderStep4;
import io.camunda.client.api.command.enums.TenantFilter;
import java.net.URI;
import java.time.Duration;
import java.util.List;

public final class ClientProperties {

  /**
   * @see CamundaClientBuilder#applyEnvironmentVariableOverrides(boolean)
   */
  public static final String APPLY_ENVIRONMENT_VARIABLES_OVERRIDES =
      "camunda.client.applyEnvironmentVariableOverrides";

  /**
   * @see CamundaClientBuilder#preferRestOverGrpc(boolean)
   */
  public static final String PREFER_REST_OVER_GRPC = "camunda.client.gateway.preferRestOverGrpc";

  /**
   * @see CamundaClientBuilder#maxHttpConnections(int)
   */
  public static final String MAX_HTTP_CONNECTIONS = "camunda.client.gateway.maxHttpConnections";

  /**
   * @see CamundaClientBuilder#restAddress(URI)
   */
  public static final String REST_ADDRESS = "camunda.client.gateway.rest.address";

  /**
   * @see CamundaClientBuilder#grpcAddress(URI)
   */
  public static final String GRPC_ADDRESS = "camunda.client.gateway.grpc.address";

  /**
   * @see CamundaClientBuilder#defaultTenantId(String)
   */
  public static final String DEFAULT_TENANT_ID = "camunda.client.tenantId";

  /**
   * @see CamundaClientBuilder#defaultJobWorkerTenantIds(List)
   */
  public static final String DEFAULT_JOB_WORKER_TENANT_IDS = "camunda.client.worker.tenantIds";

  /**
   * @see CamundaClientBuilder#defaultJobWorkerTenantFilter(TenantFilter)
   */
  public static final String DEFAULT_JOB_WORKER_TENANT_FILTER_MODE =
      "camunda.client.worker.TenantFilter";

  /**
   * @see CamundaClientBuilder#numJobWorkerExecutionThreads(int)
   */
  public static final String JOB_WORKER_EXECUTION_THREADS = "camunda.client.worker.threads";

  /**
   * @see CamundaClientBuilder#defaultJobWorkerMaxJobsActive(int)
   */
  public static final String JOB_WORKER_MAX_JOBS_ACTIVE = "camunda.client.worker.maxJobsActive";

  /**
   * @see CamundaClientBuilder#defaultJobWorkerName(String)
   */
  public static final String DEFAULT_JOB_WORKER_NAME = "camunda.client.worker.name";

  /**
   * @see CamundaClientBuilder#defaultJobTimeout(Duration)
   */
  public static final String DEFAULT_JOB_TIMEOUT = "camunda.client.job.timeout";

  /**
   * @see CamundaClientBuilder#defaultJobPollInterval(Duration)
   */
  public static final String DEFAULT_JOB_POLL_INTERVAL = "camunda.client.job.pollinterval";

  /**
   * @see CamundaClientBuilder#defaultMessageTimeToLive(Duration)
   */
  public static final String DEFAULT_MESSAGE_TIME_TO_LIVE = "camunda.client.message.timeToLive";

  /**
   * @see CamundaClientBuilder#defaultRequestTimeout(Duration)
   */
  public static final String DEFAULT_REQUEST_TIMEOUT = "camunda.client.requestTimeout";

  /**
   * @see CamundaClientBuilder#defaultRequestTimeoutOffset(Duration)
   */
  public static final String DEFAULT_REQUEST_TIMEOUT_OFFSET = "camunda.client.requestTimeoutOffset";

  /**
   * @see CamundaClientBuilder#caCertificatePath(String)
   */
  public static final String CA_CERTIFICATE_PATH = "camunda.client.security.certpath";

  /**
   * @see CamundaClientBuilder#keepAlive(Duration)
   */
  public static final String KEEP_ALIVE = "camunda.client.keepalive";

  /**
   * @see CamundaClientBuilder#overrideAuthority(String)
   */
  public static final String OVERRIDE_AUTHORITY = "camunda.client.overrideauthority";

  /**
   * @see CamundaClientBuilder#maxMessageSize(int) (String)
   */
  public static final String MAX_MESSAGE_SIZE = "camunda.client.maxMessageSize";

  /**
   * @see CamundaClientBuilder#maxMetadataSize(int)
   */
  public static final String MAX_METADATA_SIZE = "camunda.client.maxMetadataSize";

  /**
   * @see CamundaClientCloudBuilderStep1#withClusterId(String)
   */
  public static final String CLOUD_CLUSTER_ID = "camunda.client.cloud.clusterId";

  /**
   * @see CamundaClientCloudBuilderStep2#withClientId(String)
   */
  public static final String CLOUD_CLIENT_ID = "camunda.client.cloud.clientId";

  /**
   * @see CamundaClientCloudBuilderStep3#withClientSecret( String)
   */
  public static final String CLOUD_CLIENT_SECRET = "camunda.client.cloud.secret";

  /**
   * @see CamundaClientCloudBuilderStep4#withRegion(String)
   */
  public static final String CLOUD_REGION = "camunda.client.cloud.region";

  /**
   * @see CamundaClientBuilder#defaultJobWorkerStreamEnabled(boolean)
   */
  public static final String STREAM_ENABLED = "camunda.client.worker.stream.enabled";

  /**
   * @see CamundaClientBuilder#useDefaultRetryPolicy(boolean)
   */
  public static final String USE_DEFAULT_RETRY_POLICY = "camunda.client.useDefaultRetryPolicy";

  private ClientProperties() {}
}

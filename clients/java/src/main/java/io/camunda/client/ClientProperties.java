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

import java.net.URI;
import java.time.Duration;
import java.util.List;

public final class ClientProperties {

  /**
   * @see CamundaClientBuilder#applyEnvironmentVariableOverrides(boolean)
   */
  public static final String APPLY_ENVIRONMENT_VARIABLES_OVERRIDES =
      "zeebe.client.applyEnvironmentVariableOverrides";

  /**
   * @deprecated since 8.5 for removal with 8.8, replaced by {@link ClientProperties#GRPC_ADDRESS}
   * @see CamundaClientBuilder#gatewayAddress(String)
   */
  @Deprecated public static final String GATEWAY_ADDRESS = "zeebe.client.gateway.address";

  /**
   * @deprecated since 8.5 for removal with 8.8, where toggling between both will not be possible
   * @see CamundaClientBuilder#preferRestOverGrpc(boolean)
   */
  @Deprecated
  public static final String PREFER_REST_OVER_GRPC = "zeebe.client.gateway.preferRestOverGrpc";

  /**
   * @see CamundaClientBuilder#restAddress(URI)
   */
  public static final String REST_ADDRESS = "zeebe.client.gateway.rest.address";

  /**
   * @see CamundaClientBuilder#grpcAddress(URI)
   */
  public static final String GRPC_ADDRESS = "zeebe.client.gateway.grpc.address";

  /**
   * @see CamundaClientBuilder#defaultTenantId(String)
   */
  public static final String DEFAULT_TENANT_ID = "zeebe.client.tenantId";

  /**
   * @see CamundaClientBuilder#defaultJobWorkerTenantIds(List)
   */
  public static final String DEFAULT_JOB_WORKER_TENANT_IDS = "zeebe.client.worker.tenantIds";

  /**
   * @see CamundaClientBuilder#numJobWorkerExecutionThreads(int)
   */
  public static final String JOB_WORKER_EXECUTION_THREADS = "zeebe.client.worker.threads";

  /**
   * @see CamundaClientBuilder#defaultJobWorkerMaxJobsActive(int)
   */
  public static final String JOB_WORKER_MAX_JOBS_ACTIVE = "zeebe.client.worker.maxJobsActive";

  /**
   * @see CamundaClientBuilder#defaultJobWorkerName(String)
   */
  public static final String DEFAULT_JOB_WORKER_NAME = "zeebe.client.worker.name";

  /**
   * @see CamundaClientBuilder#defaultJobTimeout(Duration)
   */
  public static final String DEFAULT_JOB_TIMEOUT = "zeebe.client.job.timeout";

  /**
   * @see CamundaClientBuilder#defaultJobPollInterval(Duration)
   */
  public static final String DEFAULT_JOB_POLL_INTERVAL = "zeebe.client.job.pollinterval";

  /**
   * @see CamundaClientBuilder#defaultMessageTimeToLive(Duration)
   */
  public static final String DEFAULT_MESSAGE_TIME_TO_LIVE = "zeebe.client.message.timeToLive";

  /**
   * @see CamundaClientBuilder#defaultRequestTimeout(Duration)
   */
  public static final String DEFAULT_REQUEST_TIMEOUT = "zeebe.client.requestTimeout";

  /**
   * @see CamundaClientBuilder#usePlaintext()
   */
  public static final String USE_PLAINTEXT_CONNECTION = "zeebe.client.security.plaintext";

  /**
   * @see CamundaClientBuilder#caCertificatePath(String)
   */
  public static final String CA_CERTIFICATE_PATH = "zeebe.client.security.certpath";

  /**
   * @see CamundaClientBuilder#keepAlive(Duration)
   */
  public static final String KEEP_ALIVE = "zeebe.client.keepalive";

  /**
   * @see CamundaClientBuilder#overrideAuthority(String)
   */
  public static final String OVERRIDE_AUTHORITY = "zeebe.client.overrideauthority";

  /**
   * @see CamundaClientBuilder#maxMessageSize(int) (String)
   */
  public static final String MAX_MESSAGE_SIZE = "zeebe.client.maxMessageSize";

  /**
   * @see CamundaClientBuilder#maxMetadataSize(int)
   */
  public static final String MAX_METADATA_SIZE = "zeebe.client.maxMetadataSize";

  /**
   * @see CamundaClientCloudBuilderStep1#withClusterId(String)
   */
  public static final String CLOUD_CLUSTER_ID = "zeebe.client.cloud.clusterId";

  /**
   * @see CamundaClientCloudBuilderStep1.CamundaClientCloudBuilderStep2#withClientId(String)
   */
  public static final String CLOUD_CLIENT_ID = "zeebe.client.cloud.clientId";

  /**
   * @see
   *     CamundaClientCloudBuilderStep1.CamundaClientCloudBuilderStep2.CamundaClientCloudBuilderStep3#withClientSecret(
   *     String)
   */
  public static final String CLOUD_CLIENT_SECRET = "zeebe.client.cloud.secret";

  /**
   * @see
   *     CamundaClientCloudBuilderStep1.CamundaClientCloudBuilderStep2.CamundaClientCloudBuilderStep3.CamundaClientCloudBuilderStep4#withRegion(String)
   */
  public static final String CLOUD_REGION = "zeebe.client.cloud.region";

  /**
   * @see CamundaClientBuilder#defaultJobWorkerStreamEnabled(boolean)
   */
  public static final String STREAM_ENABLED = "zeebe.client.worker.stream.enabled";

  /**
   * @see CamundaClientBuilder#useDefaultRetryPolicy(boolean)
   */
  public static final String USE_DEFAULT_RETRY_POLICY = "zeebe.client.useDefaultRetryPolicy";

  private ClientProperties() {}
}

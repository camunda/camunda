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

import io.camunda.zeebe.client.ZeebeClientCloudBuilderStep1.ZeebeClientCloudBuilderStep2;
import io.camunda.zeebe.client.ZeebeClientCloudBuilderStep1.ZeebeClientCloudBuilderStep2.ZeebeClientCloudBuilderStep3;
import io.camunda.zeebe.client.ZeebeClientCloudBuilderStep1.ZeebeClientCloudBuilderStep2.ZeebeClientCloudBuilderStep3.ZeebeClientCloudBuilderStep4;
import java.net.URI;
import java.time.Duration;
import java.util.List;

/**
 * @deprecated since 8.8 for removal in 8.9, replaced by {@link io.camunda.client.ClientProperties}
 */
@Deprecated
public final class ClientProperties {

  /**
   * @see ZeebeClientBuilder#applyEnvironmentVariableOverrides(boolean)
   */
  public static final String APPLY_ENVIRONMENT_VARIABLES_OVERRIDES =
      "zeebe.client.applyEnvironmentVariableOverrides";

  /**
   * @deprecated since 8.5 for removal with 8.8, replaced by {@link ClientProperties#GRPC_ADDRESS}
   * @see ZeebeClientBuilder#gatewayAddress(String)
   */
  @Deprecated public static final String GATEWAY_ADDRESS = "zeebe.client.gateway.address";

  /**
   * @deprecated since 8.5 for removal with 8.8, where toggling between both will not be possible
   * @see ZeebeClientBuilder#preferRestOverGrpc(boolean)
   */
  @Deprecated
  public static final String PREFER_REST_OVER_GRPC = "zeebe.client.gateway.preferRestOverGrpc";

  /**
   * @see ZeebeClientBuilder#restAddress(URI)
   */
  public static final String REST_ADDRESS = "zeebe.client.gateway.rest.address";

  /**
   * @see ZeebeClientBuilder#grpcAddress(URI)
   */
  public static final String GRPC_ADDRESS = "zeebe.client.gateway.grpc.address";

  /**
   * @see ZeebeClientBuilder#defaultTenantId(String)
   */
  public static final String DEFAULT_TENANT_ID = "zeebe.client.tenantId";

  /**
   * @see ZeebeClientBuilder#defaultJobWorkerTenantIds(List)
   */
  public static final String DEFAULT_JOB_WORKER_TENANT_IDS = "zeebe.client.worker.tenantIds";

  /**
   * @see ZeebeClientBuilder#numJobWorkerExecutionThreads(int)
   */
  public static final String JOB_WORKER_EXECUTION_THREADS = "zeebe.client.worker.threads";

  /**
   * @see ZeebeClientBuilder#defaultJobWorkerMaxJobsActive(int)
   */
  public static final String JOB_WORKER_MAX_JOBS_ACTIVE = "zeebe.client.worker.maxJobsActive";

  /**
   * @see ZeebeClientBuilder#defaultJobWorkerName(String)
   */
  public static final String DEFAULT_JOB_WORKER_NAME = "zeebe.client.worker.name";

  /**
   * @see ZeebeClientBuilder#defaultJobTimeout(Duration)
   */
  public static final String DEFAULT_JOB_TIMEOUT = "zeebe.client.job.timeout";

  /**
   * @see ZeebeClientBuilder#defaultJobPollInterval(Duration)
   */
  public static final String DEFAULT_JOB_POLL_INTERVAL = "zeebe.client.job.pollinterval";

  /**
   * @see ZeebeClientBuilder#defaultMessageTimeToLive(Duration)
   */
  public static final String DEFAULT_MESSAGE_TIME_TO_LIVE = "zeebe.client.message.timeToLive";

  /**
   * @see ZeebeClientBuilder#defaultRequestTimeout(Duration)
   */
  public static final String DEFAULT_REQUEST_TIMEOUT = "zeebe.client.requestTimeout";

  /**
   * @see ZeebeClientBuilder#usePlaintext()
   */
  public static final String USE_PLAINTEXT_CONNECTION = "zeebe.client.security.plaintext";

  /**
   * @see ZeebeClientBuilder#caCertificatePath(String)
   */
  public static final String CA_CERTIFICATE_PATH = "zeebe.client.security.certpath";

  /**
   * @see ZeebeClientBuilder#keepAlive(Duration)
   */
  public static final String KEEP_ALIVE = "zeebe.client.keepalive";

  /**
   * @see ZeebeClientBuilder#overrideAuthority(String)
   */
  public static final String OVERRIDE_AUTHORITY = "zeebe.client.overrideauthority";

  /**
   * @see ZeebeClientBuilder#maxMessageSize(int) (String)
   */
  public static final String MAX_MESSAGE_SIZE = "zeebe.client.maxMessageSize";

  /**
   * @see ZeebeClientBuilder#maxMetadataSize(int)
   */
  public static final String MAX_METADATA_SIZE = "zeebe.client.maxMetadataSize";

  /**
   * @see ZeebeClientCloudBuilderStep1#withClusterId(String)
   */
  public static final String CLOUD_CLUSTER_ID = "zeebe.client.cloud.clusterId";

  /**
   * @see ZeebeClientCloudBuilderStep2#withClientId(String)
   */
  public static final String CLOUD_CLIENT_ID = "zeebe.client.cloud.clientId";

  /**
   * @see ZeebeClientCloudBuilderStep3#withClientSecret( String)
   */
  public static final String CLOUD_CLIENT_SECRET = "zeebe.client.cloud.secret";

  /**
   * @see ZeebeClientCloudBuilderStep4#withRegion(String)
   */
  public static final String CLOUD_REGION = "zeebe.client.cloud.region";

  /**
   * @see ZeebeClientBuilder#defaultJobWorkerStreamEnabled(boolean)
   */
  public static final String STREAM_ENABLED = "zeebe.client.worker.stream.enabled";

  /**
   * @see ZeebeClientBuilder#useDefaultRetryPolicy(boolean)
   */
  public static final String USE_DEFAULT_RETRY_POLICY = "zeebe.client.useDefaultRetryPolicy";

  private ClientProperties() {}
}

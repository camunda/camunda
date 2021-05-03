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
package io.zeebe.client;

import java.time.Duration;

public final class ClientProperties {

  /** @see ZeebeClientBuilder#gatewayAddress(String) */
  public static final String GATEWAY_ADDRESS = "zeebe.client.gateway.address";

  /** @see ZeebeClientBuilder#numJobWorkerExecutionThreads(int) */
  public static final String JOB_WORKER_EXECUTION_THREADS = "zeebe.client.worker.threads";

  /** @see ZeebeClientBuilder#defaultJobWorkerMaxJobsActive(int) */
  public static final String JOB_WORKER_MAX_JOBS_ACTIVE = "zeebe.client.worker.maxJobsActive";

  /** @see ZeebeClientBuilder#defaultJobWorkerName(String) */
  public static final String DEFAULT_JOB_WORKER_NAME = "zeebe.client.worker.name";

  /** @see ZeebeClientBuilder#defaultJobTimeout(java.time.Duration) */
  public static final String DEFAULT_JOB_TIMEOUT = "zeebe.client.job.timeout";

  /** @see ZeebeClientBuilder#defaultJobPollInterval(Duration) */
  public static final String DEFAULT_JOB_POLL_INTERVAL = "zeebe.client.job.pollinterval";

  /** @see ZeebeClientBuilder#defaultMessageTimeToLive(java.time.Duration) */
  public static final String DEFAULT_MESSAGE_TIME_TO_LIVE = "zeebe.client.message.timeToLive";

  /** @see ZeebeClientBuilder#defaultRequestTimeout(Duration) */
  public static final String DEFAULT_REQUEST_TIMEOUT = "zeebe.client.requestTimeout";

  /** @see ZeebeClientBuilder#usePlaintext() */
  public static final String USE_PLAINTEXT_CONNECTION = "zeebe.client.security.plaintext";

  /** @see ZeebeClientBuilder#caCertificatePath(String) */
  public static final String CA_CERTIFICATE_PATH = "zeebe.client.security.certpath";

  /** @see io.zeebe.client.ZeebeClientBuilder#keepAlive(Duration) */
  public static final String KEEP_ALIVE = "zeebe.client.keepalive";

  /** @see ZeebeClientCloudBuilderStep1#withClusterId(java.lang.String) */
  public static final String CLOUD_CLUSTER_ID = "zeebe.client.cloud.clusterId";

  /**
   * @see ZeebeClientCloudBuilderStep1.ZeebeClientCloudBuilderStep2#withClientId(java.lang.String)
   */
  public static final String CLOUD_CLIENT_ID = "zeebe.client.cloud.clientId";

  /**
   * @see
   *     ZeebeClientCloudBuilderStep1.ZeebeClientCloudBuilderStep2.ZeebeClientCloudBuilderStep3#withClientSecret(
   *     java.lang.String)
   */
  public static final String CLOUD_CLIENT_SECRET = "zeebe.client.cloud.secret";

  private ClientProperties() {}
}

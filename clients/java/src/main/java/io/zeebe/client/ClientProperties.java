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

public class ClientProperties {
  /** @see ZeebeClientBuilder#brokerContactPoint(String) */
  public static final String BROKER_CONTACTPOINT = "zeebe.client.broker.contactPoint";

  /** @see ZeebeClientBuilder#numJobWorkerExecutionThreads(int) */
  public static final String JOB_WORKER_EXECUTION_THREADS = "zeebe.client.worker.threads";

  /** @see ZeebeClientBuilder#defaultJobWorkerMaxJobsActive(int) */
  public static final String JOB_WORKER_MAX_JOBS_ACTIVE = "zeebe.client.worker.maxJobsActive";

  /** @see ZeebeClientBuilder#defaultJobWorkerName(String) */
  public static final String DEFAULT_JOB_WORKER_NAME = "zeebe.client.job.worker";

  /** @see ZeebeClientBuilder#defaultJobTimeout(java.time.Duration) */
  public static final String DEFAULT_JOB_TIMEOUT = "zeebe.client.job.timeout";

  /** @see ZeebeClientBuilder#defaultJobPollInterval(Duration) */
  public static final String DEFAULT_JOB_POLL_INTERVAL = "zeebe.client.job.pollinterval";

  /** @see ZeebeClientBuilder#defaultMessageTimeToLive(java.time.Duration) */
  public static final String DEFAULT_MESSAGE_TIME_TO_LIVE = "zeebe.client.message.timeToLive";
}

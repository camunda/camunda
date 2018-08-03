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

public class ClientProperties {
  /** @see ZeebeClientBuilder#brokerContactPoint(String) */
  public static final String BROKER_CONTACTPOINT = "zeebe.client.broker.contactPoint";

  /** @see ZeebeClientBuilder#requestBlocktime(java.time.Duration) */
  public static final String REQUEST_BLOCKTIME_MILLIS = "zeebe.client.requestBlockTime";

  /** @see ZeebeClientBuilder#sendBufferSize(int) */
  public static final String SENDBUFFER_SIZE = "zeebe.client.sendbuffer.size";

  /** @see ZeebeClientBuilder#numManagementThreads(int) */
  public static final String MANAGEMENT_THREADS = "zeebe.client.threads";

  /** @see ZeebeClientBuilder#numSubscriptionExecutionThreads(int) */
  public static final String SUBSCRIPTION_EXECUTION_THREADS = "zeebe.client.subscription.threads";

  /** @see ZeebeClientBuilder#defaultTopicSubscriptionBufferSize(int) */
  public static final String TOPIC_SUBSCRIPTION_BUFFER_SIZE =
      "zeebe.client.subscription.buffersize";

  /** @see ZeebeClientBuilder#defaultTopicSubscriptionBufferSize(int) */
  public static final String JOB_SUBSCRIPTION_BUFFER_SIZE = "zeebe.client.worker.buffersize";

  /** @see ZeebeClientBuilder#tcpChannelKeepAlivePeriod(java.time.Duration) */
  public static final String TCP_CHANNEL_KEEP_ALIVE_PERIOD = "zeebe.client.channel.keepalive";

  /** @see ZeebeClientBuilder#requestTimeout(java.time.Duration) */
  public static final String REQUEST_TIMEOUT_SEC = "zeebe.client.request.timeout";

  /** @see ZeebeClientBuilder#defaultJobWorkerName(String) */
  public static final String DEFAULT_JOB_WORKER_NAME = "zeebe.client.job.worker";

  /** @see ZeebeClientBuilder#defaultJobTimeout(java.time.Duration) */
  public static final String DEFAULT_JOB_TIMEOUT = "zeebe.client.job.timeout";

  /** @see ZeebeClientBuilder#defaultTopic(String) */
  public static final String DEFAULT_TOPIC = "zeebe.client.defaultTopic";
}

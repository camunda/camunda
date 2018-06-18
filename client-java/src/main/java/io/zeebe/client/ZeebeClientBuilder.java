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
   * @param contactPoint the IP socket address of a broker that the client can initially connect to.
   *     Must be in format <code>host:port</code>. The default value is <code>127.0.0.1:51015</code>
   *     .
   */
  ZeebeClientBuilder brokerContactPoint(String contactPoint);

  /** @param requestTimeout The request timeout in seconds. Default value is 15 seconds. */
  ZeebeClientBuilder requestTimeout(Duration requestTimeout);

  /**
   * @param requestBlockTime The time to block when the user submits a request and the client has no
   *     buffer (pooling) capacity to send the request. After this time, an exception is thrown.
   *     Default value is 15 seconds.
   */
  ZeebeClientBuilder requestBlocktime(Duration requestBlockTime);

  /** @param sendBufferSize the size of the client's send buffer in MB. Default value is 16. */
  ZeebeClientBuilder sendBufferSize(int sendBufferSize);

  /**
   * @param numManagementThreads The number of threads the client uses for the following tasks:
   *     <ul>
   *       <li>Orchestrating requests
   *       <li>Sending/Receiving network traffic
   *       <li>Orchestrating subscriptions
   *     </ul>
   *     Default value is 1.
   */
  ZeebeClientBuilder numManagementThreads(int numManagementThreads);

  /**
   * @param numSubscriptionThreads The number of threads for invocation of job workers and topic
   *     subscriptions. Setting this value to 0 effectively disables subscriptions and workers.
   *     Default value is 1.
   */
  ZeebeClientBuilder numSubscriptionExecutionThreads(int numSubscriptionThreads);

  /**
   * @param numberOfRecords Default value for {@link
   *     io.zeebe.client.api.subscription.TopicSubscriptionBuilderStep1.TopicSubscriptionBuilderStep3#bufferSize(int)}.
   *     Default value is 1024.
   */
  ZeebeClientBuilder defaultTopicSubscriptionBufferSize(int numberOfRecords);

  /**
   * @param numberOfJobs Default value for {@link
   *     io.zeebe.client.api.subscription.JobWorkerBuilderStep1.JobWorkerBuilderStep3#bufferSize(int)}.
   *     Default value is 32.
   */
  ZeebeClientBuilder defaultJobSubscriptionBufferSize(int numberOfJobs);

  /**
   * The period of time in milliseconds for sending keep alive messages on tcp channels. Setting
   * this appropriately can avoid overhead by reopening channels after idle time. Default value is 5
   * seconds.
   */
  ZeebeClientBuilder tcpChannelKeepAlivePeriod(Duration tcpChannelKeepAlivePeriod);

  /**
   * The name of the worker which is used when none is set for a job worker. Default is 'default'.
   */
  ZeebeClientBuilder defaultJobWorkerName(String workerName);

  /** The timeout which is used when none is provided for a job worker. Default is 5 minutes. */
  ZeebeClientBuilder defaultJobTimeout(Duration timeout);

  /**
   * The name of the topic which is used for {@link ZeebeClient#topicClient()}. Default is
   * "default-topic".
   */
  ZeebeClientBuilder defaultTopic(String topic);

  /** @return a new {@link ZeebeClient} with the provided configuration options. */
  ZeebeClient build();
}

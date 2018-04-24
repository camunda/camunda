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

public interface ZeebeClientBuilder
{

    /**
     * @param contactPoint the IP socket address of a broker that the client can initially connect to.
     * Must be in format <code>host:port</code>. The default value is <code>127.0.0.1:51015</code>.
     */
    ZeebeClientBuilder brokerContactPoint(String contactPoint);

    /**
     * @param requestTimeout The request timeout in seconds. Default value is 15 seconds.
     */
    ZeebeClientBuilder requestTimeout(Duration requestTimeout);

    /**
     * @param requestBlockTime The time to block when the user submits a request and the client
     * has no buffer (pooling) capacity to send the request. After this time, an exception is thrown.
     * Default value is 15 seconds.
     */
    ZeebeClientBuilder requestBlocktime(Duration requestBlockTime);

    /**
     * @param sendBufferSize the size of the client's send buffer in MB. Default value is 16.
     */
    ZeebeClientBuilder sendBufferSize(int sendBufferSize);

    /**
     * @param numManagementThreads The number of threads the client uses for the following tasks:
     *
     * <ul>
     * <li>Orchestrating requests
     * <li>Sending/Receiving network traffic
     * <li>Orchestrating subscriptions
     * </ul>
     *
     * Default value is 1.
     */
    ZeebeClientBuilder numManagementThreads(int numManagementThreads);

    /**
     * @param numSubscriptionThreads The number of threads for invocation of managed task and
     * topic subscriptions. Setting this value to 0 effectively disables managed subscriptions.
     * Default value is 1.
     */
    ZeebeClientBuilder numSubscriptionExecutionThreads(int numSubscriptionThreads);

    /**
     * @param topicSubscriptionPrefetchCapacity Determines the maximum amount of topic events
     * that are prefetched and buffered at a time before they are handled to the event handler.
     * Default value is 32.
     */
    ZeebeClientBuilder topicSubscriptionPrefetchCapacity(int topicSubscriptionPrefetchCapacity);

    /**
     * The period of time in milliseconds for sending keep alive messages on tcp channels.
     * Setting this appropriately can avoid overhead by reopening channels after idle time.
     * Default value is 5 seconds.
     */
    ZeebeClientBuilder tcpChannelKeepAlivePeriod(Duration tcpChannelKeepAlivePeriod);

    /**
     * The lock owner which is used when no owner is set for the job
     * subscription. Default is 'default'.
     */
    ZeebeClientBuilder defaultJobLockOwner(String jobOwner);

    /**
     * The lock time which is used when no time is set for the job subscription.
     * Default is 5 minutes.
     */
    ZeebeClientBuilder defaultJobLockTime(Duration lockTime);

    /**
     * @return a new {@link ZeebeClient} with the provided configuration options.
     */
    io.zeebe.client.ZeebeClient create();
}

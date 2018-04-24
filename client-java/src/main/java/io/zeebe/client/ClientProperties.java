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

public class ClientProperties
{
    /**
     * Either a hostname if the broker is running on the default port or hostname:port
     */
    public static final String BROKER_CONTACTPOINT = "zeebe.client.broker.contactPoint";

    /**
     * The time in milliseconds to block when the user submits a request and the client
     * has no buffer (pooling) capacity to send the request. After this time, an exception is thrown.
     */
    public static final String CLIENT_REQUEST_BLOCKTIME_MILLIS = "zeebe.client.requestBlockTime";

    /**
     * the size of the client's send buffer in MB
     */
    public static final String CLIENT_SENDBUFFER_SIZE = "zeebe.client.sendbuffer.size";

    /**
     * The number of threads the client uses for the following tasks:
     *
     * <ul>
     * <li>Orchestrating requests
     * <li>Sending/Receiving network traffic
     * <li>Orchestrating subscriptions
     */
    public static final String CLIENT_MANAGEMENT_THREADS = "zeebe.client.threads";

    /**
     * The number of threads for invocation of managed task and topic subscriptions. Setting this value to
     * 0 effectively disables managed subscriptions.
     */
    public static final String CLIENT_SUBSCRIPTION_EXECUTION_THREADS = "zeebe.client.subscription.threads";


    /**
     * Determines the maximum amount of topic events are prefetched and buffered at a time
     * before they are handled to the event handler. Default value is 32.
     */
    public static final String CLIENT_TOPIC_SUBSCRIPTION_PREFETCH_CAPACITY = "zeebe.client.subscription.prefetch";

    /**
     * The period of time in milliseconds for sending keep alive messages on tcp channels. Setting this appropriately
     * can avoid overhead by reopening channels after idle time.
     */
    /*
     * Optional property; Default is defined by transport
     */
    public static final String CLIENT_TCP_CHANNEL_KEEP_ALIVE_PERIOD = "zeebe.client.channel.keepalive";

    /**
     * The request timeout in seconds.
     */
    public static final String CLIENT_REQUEST_TIMEOUT_SEC = "zeebe.client.request.timeout";

    /**
     * The lock owner which is used when no owner is set for the job
     * subscription. Default is 'default'.
     */
    public static final String CLIENT_JOB_DEFAULT_LOCK_OWNER = "zeebe.client.job.lockOwner";

    /**
     * The lock time in milliseconds which is used when no time is set for the
     * job subscription. Default is 5 minutes.
     */
    public static final String CLIENT_JOB_DEFAULT_LOCK_TIME = "zeebe.client.job.lockTime";

}

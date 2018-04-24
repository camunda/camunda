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
package io.zeebe.client.impl;

import static io.zeebe.client.ClientProperties.*;

import java.time.Duration;
import java.util.Properties;

import io.zeebe.client.*;
import io.zeebe.util.sched.clock.ActorClock;

public class ZeebeClientBuilderImpl implements ZeebeClientBuilder, ZeebeClientConfiguration
{
    private String brokerContactPoint = "127.0.0.1:51015";
    private Duration requestTimeout = Duration.ofSeconds(15);
    private Duration requestBlocktime = Duration.ofSeconds(15);
    private int sendBufferSize = 16;
    private int numManagementThreads = 1;
    private int numSubscriptionExecutionThreads = 1;
    private int topicSubscriptionPrefetchCapacity = 32;
    private Duration tcpChannelKeepAlivePeriod;
    private ActorClock actorClock;
    private String defaultJobLockOwner = "default";
    private Duration defaultJobLockTime = Duration.ofMinutes(5);

    @Override
    public String getBrokerContactPoint()
    {
        return brokerContactPoint;
    }

    @Override
    public ZeebeClientBuilder brokerContactPoint(String contactPoint)
    {
        this.brokerContactPoint = contactPoint;
        return this;
    }

    @Override
    public Duration getRequestTimeout()
    {
        return requestTimeout;
    }

    @Override
    public ZeebeClientBuilder requestTimeout(Duration requestTimeout)
    {
        this.requestTimeout = requestTimeout;
        return this;
    }

    @Override
    public Duration getRequestBlocktime()
    {
        return requestBlocktime;
    }

    @Override
    public ZeebeClientBuilder requestBlocktime(Duration requestBlockTime)
    {
        this.requestBlocktime = requestBlockTime;
        return this;
    }

    @Override
    public int getSendBufferSize()
    {
        return sendBufferSize;
    }

    @Override
    public ZeebeClientBuilder sendBufferSize(int sendBufferSize)
    {
        this.sendBufferSize = sendBufferSize;
        return this;
    }

    @Override
    public int getNumManagementThreads()
    {
        return numManagementThreads;
    }

    @Override
    public ZeebeClientBuilder numManagementThreads(int numManagementThreads)
    {
        this.numManagementThreads = numManagementThreads;
        return this;
    }

    @Override
    public int getNumSubscriptionExecutionThreads()
    {
        return numSubscriptionExecutionThreads;
    }

    @Override
    public ZeebeClientBuilder numSubscriptionExecutionThreads(int numSubscriptionThreads)
    {
        this.numSubscriptionExecutionThreads = numSubscriptionThreads;
        return this;
    }

    @Override
    public int getTopicSubscriptionPrefetchCapacity()
    {
        return topicSubscriptionPrefetchCapacity;
    }

    @Override
    public ZeebeClientBuilder topicSubscriptionPrefetchCapacity(int topicSubscriptionPrefetchCapacity)
    {
        this.topicSubscriptionPrefetchCapacity = topicSubscriptionPrefetchCapacity;
        return this;
    }

    @Override
    public Duration getTcpChannelKeepAlivePeriod()
    {
        return tcpChannelKeepAlivePeriod;
    }

    @Override
    public ZeebeClientBuilder tcpChannelKeepAlivePeriod(Duration tcpChannelKeepAlivePeriod)
    {
        this.tcpChannelKeepAlivePeriod = tcpChannelKeepAlivePeriod;
        return this;
    }

    public ActorClock getActorClock()
    {
        return actorClock;
    }

    public ZeebeClientBuilder setActorClock(ActorClock actorClock)
    {
        this.actorClock = actorClock;
        return this;
    }

    @Override
    public String getDefaultJobLockOwner()
    {
        return defaultJobLockOwner;
    }

    @Override
    public Duration getDefaultJobLockTime()
    {
        return defaultJobLockTime;
    }

    @Override
    public ZeebeClientBuilder defaultJobLockOwner(String jobOwner)
    {
        this.defaultJobLockOwner = jobOwner;
        return this;
    }

    @Override
    public ZeebeClientBuilder defaultJobLockTime(Duration lockTime)
    {
        this.defaultJobLockTime = lockTime;
        return this;
    }

    @Override
    public ZeebeClient create()
    {
        return new ZeebeClientImpl(this, actorClock);
    }

    public static ZeebeClientBuilderImpl fromProperties(Properties properties)
    {
        final ZeebeClientBuilderImpl builder = new ZeebeClientBuilderImpl();

        if (properties.containsKey(ClientProperties.BROKER_CONTACTPOINT))
        {
            builder.brokerContactPoint(properties.getProperty(ClientProperties.BROKER_CONTACTPOINT));
        }
        if (properties.containsKey(ClientProperties.CLIENT_REQUEST_TIMEOUT_SEC))
        {
            builder.requestTimeout(Duration.ofSeconds(Integer.parseInt(properties.getProperty(ClientProperties.CLIENT_REQUEST_TIMEOUT_SEC))));
        }
        if (properties.containsKey(CLIENT_REQUEST_BLOCKTIME_MILLIS))
        {
            builder.requestBlocktime(Duration.ofMillis(Integer.parseInt(properties.getProperty(CLIENT_REQUEST_BLOCKTIME_MILLIS))));
        }
        if (properties.containsKey(CLIENT_SENDBUFFER_SIZE))
        {
            builder.sendBufferSize(Integer.parseInt(properties.getProperty(CLIENT_SENDBUFFER_SIZE)));
        }
        if (properties.containsKey(ClientProperties.CLIENT_MANAGEMENT_THREADS))
        {
            builder.numManagementThreads(Integer.parseInt(properties.getProperty(ClientProperties.CLIENT_MANAGEMENT_THREADS)));
        }
        if (properties.containsKey(ClientProperties.CLIENT_TCP_CHANNEL_KEEP_ALIVE_PERIOD))
        {
            builder.tcpChannelKeepAlivePeriod(Duration.ofMillis(Long.parseLong(properties.getProperty(ClientProperties.CLIENT_TCP_CHANNEL_KEEP_ALIVE_PERIOD))));
        }
        if (properties.containsKey(ClientProperties.CLIENT_SUBSCRIPTION_EXECUTION_THREADS))
        {
            builder.numSubscriptionExecutionThreads(Integer.parseInt(properties.getProperty(ClientProperties.CLIENT_SUBSCRIPTION_EXECUTION_THREADS)));
        }
        if (properties.containsKey(ClientProperties.CLIENT_TOPIC_SUBSCRIPTION_PREFETCH_CAPACITY))
        {
            builder.topicSubscriptionPrefetchCapacity(Integer.parseInt(properties.getProperty(ClientProperties.CLIENT_TOPIC_SUBSCRIPTION_PREFETCH_CAPACITY)));
        }
        if (properties.containsKey(CLIENT_JOB_DEFAULT_LOCK_OWNER))
        {
            builder.defaultJobLockOwner(properties.getProperty(CLIENT_JOB_DEFAULT_LOCK_OWNER));
        }
        if (properties.containsKey(CLIENT_JOB_DEFAULT_LOCK_TIME))
        {
            builder.defaultJobLockTime(Duration.ofMillis(Integer.parseInt(properties.getProperty(CLIENT_JOB_DEFAULT_LOCK_TIME))));
        }

        return builder;
    }

    @Override
    public String toString()
    {
        final StringBuilder sb = new StringBuilder();

        appendProperty(sb, "brokerContactPoint", brokerContactPoint);
        appendProperty(sb, "requestTimeout", requestTimeout);
        appendProperty(sb, "requestBlocktime", requestBlocktime);
        appendProperty(sb, "sendBufferSize", sendBufferSize);
        appendProperty(sb, "numManagementThreads", numManagementThreads);
        appendProperty(sb, "numSubscriptionExecutionThreads", numSubscriptionExecutionThreads);
        appendProperty(sb, "topicSubscriptionPrefetchCapacity", topicSubscriptionPrefetchCapacity);
        appendProperty(sb, "tcpChannelKeepAlivePeriod", tcpChannelKeepAlivePeriod);
        appendProperty(sb, "defaultJobLockOwner", defaultJobLockOwner);
        appendProperty(sb, "defaultJobLockTime", defaultJobLockTime);

        return sb.toString();
    }

    private static void appendProperty(StringBuilder sb, String propertyName, Object value)
    {
        sb.append(propertyName + ": " + value + "\n");
    }

}

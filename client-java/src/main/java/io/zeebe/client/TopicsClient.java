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

import io.zeebe.client.cmd.Request;
import io.zeebe.client.event.Event;
import io.zeebe.client.event.PollableTopicSubscription;
import io.zeebe.client.event.PollableTopicSubscriptionBuilder;
import io.zeebe.client.event.TopicSubscriptionBuilder;
import io.zeebe.client.topic.Topics;

/**
 * Provides operations related to any topic.
 */
public interface TopicsClient
{

    /**
     * Subscribe to all events that are published to a specific topic.
     * Events handling is <i>managed</i> by the client library,
     * i.e. received events are automatically forwarded to a registered event handler.
     *
     * @return a builder for an event subscription and managed event handling
     */
    TopicSubscriptionBuilder newSubscription(String topicName);

    /**
     * Subscribe to all events that are published to a specific topic.
     * Events handling is <i>not managed</i> by the client library.
     * Use {@link PollableTopicSubscription#poll(io.zeebe.client.event.TopicEventHandler)}
     * to manually trigger event handling for any currently available event.
     *
     * @return a builder for an event subscription and manual event handling
     */
    PollableTopicSubscriptionBuilder newPollableSubscription(String topicName);

    /**
     * Creates a new topic with the given name and number of partitions.
     */
    Request<Event> create(String topicName, int partitions);
    Request<Event> create(String topicName, int partitions, int replicationFactor);

    /**
     * Requests all topics. Can be used to inspect which topics and partitions have been created.
     */
    Request<Topics> getTopics();



}

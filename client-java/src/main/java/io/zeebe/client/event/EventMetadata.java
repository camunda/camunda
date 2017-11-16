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
package io.zeebe.client.event;

public interface EventMetadata
{

    /**
     * @return the name of the topic this event as published on
     */
    String getTopicName();

    /**
     * @return the id of the topic partition this event was published on
     */
    int getPartitionId();

    /**
     * @return the unique position the event has in the topic. Events are ordered by position.
     */
    long getPosition();

    /**
     * @return the key of the event on this topic. Multiple events can have the same key if they
     *   reflect state of the same logical entity. Keys are unique for the combination of topic, partition and entity type.
     */
    long getKey();

    /**
     * @return the type of the event
     */
    TopicEventType getType();
}

/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.system.deployment.data;

import static io.zeebe.logstreams.log.LogStream.MAX_TOPIC_NAME_LENGTH;

import org.agrona.DirectBuffer;

import io.zeebe.map.Bytes2LongZbMap;

/**
 * Set of defined topics: when a deployment CREATE command is processed,
 * this set is used to verify whether the referenced topic exists.
 *<p>
 * Note that this set is populated based on the {@link TopicState#CREATING} state.
 * Why? Once a topic is in state CREATING, it should be possible to deploy workflows
 * for this topic.
 */
public class TopicNames
{
    private static final long EXISTS_VALUE = 1;
    private static final long NOT_EXISTS_VALUE = -1;

    private final Bytes2LongZbMap map = new Bytes2LongZbMap(MAX_TOPIC_NAME_LENGTH);

    public void addTopic(DirectBuffer name)
    {
        map.put(name, 0, name.capacity(), EXISTS_VALUE);
    }

    public boolean exists(DirectBuffer topicName)
    {
        return map.get(topicName, 0, topicName.capacity(), NOT_EXISTS_VALUE) == EXISTS_VALUE;
    }

    public Bytes2LongZbMap getRawMap()
    {
        return map;
    }
}

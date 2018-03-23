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
package io.zeebe.broker.topic;

import static io.zeebe.test.util.TestUtil.doRepeatedly;
import static io.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

import io.zeebe.broker.logstreams.processor.TypedStreamEnvironment;
import io.zeebe.broker.logstreams.processor.TypedStreamProcessor;
import io.zeebe.broker.system.log.PartitionEvent;
import io.zeebe.broker.system.log.PartitionResponder;
import io.zeebe.broker.system.log.PartitionState;
import io.zeebe.broker.system.log.SystemPartitionManager;
import io.zeebe.broker.system.log.TopicEvent;
import io.zeebe.broker.system.log.TopicState;
import io.zeebe.broker.util.StreamProcessorRule;
import io.zeebe.util.buffer.BufferUtil;
import io.zeebe.util.sched.future.ActorFuture;

@SuppressWarnings("unchecked")
public class RequestPartitionsStreamProcessorTest
{
    public static final String STREAM_NAME = "stream";

    @Rule
    public StreamProcessorRule rule = new StreamProcessorRule();

    private PartitionResponder partitionResponder;

    protected TypedStreamProcessor buildStreamProcessor(TypedStreamEnvironment env)
    {
        partitionResponder = new PartitionResponder(env.getOutput());
        return SystemPartitionManager.buildPartitionResponseProcessor(env, partitionResponder);
    }

    @Test
    public void shouldCompleteFutureWhenSendingPartitions()
    {
        // given
        rule.runStreamProcessor(this::buildStreamProcessor);

        // when
        final ActorFuture<Void> future = doRepeatedly(() -> partitionResponder.sendPartitions(1, 2)).until(f -> f != null);

        // then
        waitUntil(future::isDone);
        assertThat(future).isDone();
        assertThat(rule.getOutput().getSentResponses()).hasSize(1);

    }

    @Test
    public void shouldNotIncludeCreatedPartitionsWhenTopicIsNotCreated()
    {
        // given
        final String topicName = "foo";
        rule.writeEvent(partitionCreated(topicName, 1));
        final long partition2Created = rule.writeEvent(partitionCreated(topicName, 2));

        final StreamProcessorControl processorControl = rule.initStreamProcessor(this::buildStreamProcessor);
        processorControl.blockAfterEvent(e -> e.getPosition() == partition2Created);
        processorControl.start();

        waitUntil(() -> processorControl.isBlocked());

        // when
        partitionResponder.sendPartitions(1, 2);

        // then
        waitUntil(() -> rule.getOutput().getSentResponses().size() == 1);
        assertThat(rule.getOutput().getSentResponses()).hasSize(1);

        final Map<String, Object> controlMessageResponse = rule.getOutput().getAsControlMessageData(0);
        final List<Map<String, Object>> partitions = (List<Map<String, Object>>) controlMessageResponse.get("partitions");
        assertThat(partitions).isEmpty();
    }

    /**
     * Due to the retry mechanism partition of partition creation, it is possible to end up with
     * more partitions than requested. In particular, this means that the topic CREATED event is published
     * before all partition CREATED events have been published. The response should nevertheless include all partitions.
     */
    @Test
    @Ignore("https://github.com/zeebe-io/zeebe/issues/562")
    public void shouldIncludeDelayedCreatedPartitions()
    {
        // given
        final String topicName = "foo";
        final int partition1 = 1;
        final int partition2 = 2;

        rule.writeEvent(partitionCreated(topicName, partition1));
        rule.writeEvent(partitionCreated(topicName, 1));

        final long partition2Created = rule.writeEvent(partitionCreated(topicName, partition2));

        final StreamProcessorControl processorControl = rule.initStreamProcessor(this::buildStreamProcessor);
        processorControl.blockAfterEvent(e -> e.getPosition() == partition2Created);
        processorControl.start();

        waitUntil(() -> processorControl.isBlocked());

        // when
        partitionResponder.sendPartitions(1, 2);

        // then
        assertThat(rule.getOutput().getSentResponses()).hasSize(1);

        final Map<String, Object> controlMessageResponse = rule.getOutput().getAsControlMessageData(0);
        final List<Map<String, Object>> partitions = (List<Map<String, Object>>) controlMessageResponse.get("partitions");
        assertThat(partitions).hasSize(2);
        assertThat(partitions).extracting("id").containsExactlyInAnyOrder(partition1, partition2);
    }

    protected static TopicEvent topicCreated(String topicName, int numPartitions)
    {
        final TopicEvent topicEvent = new TopicEvent();
        topicEvent.setName(BufferUtil.wrapString(topicName));
        topicEvent.setPartitions(numPartitions);
        topicEvent.setState(TopicState.CREATED);

        return topicEvent;
    }

    protected static PartitionEvent partitionCreated(String topicName, int partitionId)
    {
        final PartitionEvent partitionEvent = new PartitionEvent();
        partitionEvent.setId(partitionId);
        partitionEvent.setTopicName(BufferUtil.wrapString(topicName));
        partitionEvent.setState(PartitionState.CREATED);
        partitionEvent.setCreator(BufferUtil.wrapString("foo"), 123);
        return partitionEvent;
    }
}

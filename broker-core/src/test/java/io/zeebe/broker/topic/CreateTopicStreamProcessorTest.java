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
import static org.assertj.core.api.Assertions.fail;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.zeebe.broker.clustering.member.Member;
import io.zeebe.util.collection.IntIterator;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

import io.zeebe.broker.clustering.handler.TopologyBroker;
import io.zeebe.broker.logstreams.processor.TypedEvent;
import io.zeebe.broker.logstreams.processor.TypedStreamEnvironment;
import io.zeebe.broker.logstreams.processor.TypedStreamProcessor;
import io.zeebe.broker.system.log.PartitionEvent;
import io.zeebe.broker.system.log.PartitionState;
import io.zeebe.broker.system.log.PendingPartitionsIndex;
import io.zeebe.broker.system.log.ResolvePendingPartitionsCommand;
import io.zeebe.broker.system.log.SystemPartitionManager;
import io.zeebe.broker.system.log.TopicEvent;
import io.zeebe.broker.system.log.TopicState;
import io.zeebe.broker.system.log.TopicsIndex;
import io.zeebe.broker.topic.TestPartitionManager.PartitionRequest;
import io.zeebe.broker.util.StreamProcessorRule;
import io.zeebe.transport.SocketAddress;
import io.zeebe.transport.impl.RequestResponseHeaderDescriptor;
import io.zeebe.transport.impl.TransportHeaderDescriptor;
import io.zeebe.util.buffer.BufferUtil;

public class CreateTopicStreamProcessorTest
{
    public static final Duration CREATION_EXPIRATION = Duration.ofSeconds(60);
    protected static final SocketAddress SOCKET_ADDRESS1 = new SocketAddress("saturn", 123);
    protected static final SocketAddress SOCKET_ADDRESS2 = new SocketAddress("mars", 456);

    @Rule
    public StreamProcessorRule rule = new StreamProcessorRule();

    public TestPartitionManager partitionManager;
    private ResolvePendingPartitionsCommand checkPartitionsCmd;
    private TypedStreamProcessor streamProcessor;

    @Before
    public void setUp()
    {
        this.partitionManager = new TestPartitionManager();
    }


    protected TypedStreamProcessor buildStreamProcessor(TypedStreamEnvironment env)
    {
        final TopicsIndex topicsIndex = new TopicsIndex();
        final PendingPartitionsIndex partitionsIndex = new PendingPartitionsIndex();

        checkPartitionsCmd = new ResolvePendingPartitionsCommand(
                partitionsIndex,
                partitionManager,
                env.buildStreamReader(),
                env.buildStreamWriter());

        streamProcessor = SystemPartitionManager
                .buildTopicCreationProcessor(
                    env,
                    partitionManager,
                    topicsIndex,
                    partitionsIndex,
                    CREATION_EXPIRATION,
                    () ->
                    { });

        return streamProcessor;
    }

    /**
     * Tests the case where the stream processor is slower than the interval in which
     * we check the gossip state for the leader of any pending partitions.
     */
    @Test
    public void shouldRejectSecondPartitionCompleteCommand()
    {
        // given
        // stream processor is registered and active; configured to block on first partition creating event
        partitionManager.addMember(SOCKET_ADDRESS1);

        final StreamProcessorControl processorControl = rule.runStreamProcessor(this::buildStreamProcessor);

        processorControl.blockAfterEvent(e ->
            Events.isPartitionEvent(e) &&
            Events.asPartitionEvent(e).getState() == PartitionState.CREATING);

        rule.writeEvent(createTopic("foo", 1));

        // waiting for partition creating event => the stream processor is now suspended
        waitUntil(() ->
            rule.events().onlyPartitionEvents()
                .inState(PartitionState.CREATING)
                .findFirst()
                .isPresent());

        final PartitionRequest request = partitionManager.getPartitionRequests().get(0);
        partitionManager.declarePartitionLeader(SOCKET_ADDRESS1, request.getPartitionId());

        // calling check pending partition once
        streamProcessor.runAsync(checkPartitionsCmd);

        // waiting for partition creation complete command
        waitUntil(() ->
            rule.events().onlyPartitionEvents()
                .inState(PartitionState.CREATE_COMPLETE)
                .findFirst()
                .isPresent());

        // when
        // calling check pending partition again
        streamProcessor.runAsync(checkPartitionsCmd);

        // waiting for partition creation complete command
        waitUntil(() -> rule.events().onlyPartitionEvents()                .inState(PartitionState.CREATE_COMPLETE)
            .count() == 2);

        // and resuming stream processing
        processorControl.unblock();

        // then
        waitUntil(() -> partitionEventsInState(PartitionState.CREATE_COMPLETE_REJECTED)
            .findFirst()
            .isPresent());

        final List<TypedEvent<PartitionEvent>> partitionEvents = rule.events()
            .onlyPartitionEvents()
            .collect(Collectors.toList());

        assertThat(partitionEvents).extracting("value.state")
            .containsExactly(
                    PartitionState.CREATE,
                    PartitionState.CREATING,
                    PartitionState.CREATE_COMPLETE,
                    PartitionState.CREATE_COMPLETE,
                    PartitionState.CREATED,
                    PartitionState.CREATE_COMPLETE_REJECTED);
    }

    @Test
    public void shouldNotCreatePartitionsOnRejection()
    {
        // given
        partitionManager.addMember(SOCKET_ADDRESS1);

        rule.runStreamProcessor(this::buildStreamProcessor);

        rule.writeEvent(createTopic("foo", 1));

        final PartitionEvent creatingEvent = doRepeatedly(() -> partitionEventsInState(PartitionState.CREATING).findFirst())
            .until(Optional::isPresent)
            .get()
            .getValue();

        partitionManager.declarePartitionLeader(SOCKET_ADDRESS1, creatingEvent.getId());
        streamProcessor.runAsync(checkPartitionsCmd);

        waitUntil(() -> topicEventsInState(TopicState.CREATED).findFirst().isPresent());

        // when creating the same topic again
        rule.writeEvent(createTopic("foo", 1));

        // then
        waitUntil(() -> topicEventsInState(TopicState.CREATE_REJECTED).findFirst().isPresent());

        // only the first create topic command resulted in a partition
        assertThat(partitionEventsInState(PartitionState.CREATE).count()).isEqualTo(1);
    }

    @Test
    public void shouldResendPartitionRequestToSameBrokerOnRecovery()
    {
        // given
        partitionManager.addMember(SOCKET_ADDRESS1);
        partitionManager.addMember(SOCKET_ADDRESS2);
        final StreamProcessorControl processorControl = rule.runStreamProcessor(this::buildStreamProcessor);

        rule.writeEvent(createTopic("foo", 1));

        // stream processor has processed CREATE and triggered the cluster manager to create a partition
        waitUntil(() -> partitionEventsInState(PartitionState.CREATING).findFirst().isPresent());

        final long createPosition = rule.events()
            .onlyPartitionEvents()
            .inState(PartitionState.CREATE)
            .findFirst()
            .get()
            .getPosition();

        processorControl.close();

        // removing CREATING, such that CREATE is reprocessed
        rule.truncateLog(createPosition);
        processorControl.purgeSnapshot();

        // when
        processorControl.start();

        // then
        waitUntil(() -> partitionManager.getPartitionRequests().size() == 2);

        assertThat(partitionManager.getPartitionRequests()).hasSize(2);
        assertThat(partitionManager.getPartitionRequests()).extracting("endpoint").containsOnly(SOCKET_ADDRESS1);
    }

    @Test
    public void shouldNotSendPartitionRequestToAnotherBrokerOnRecoveryIfOriginalBrokerNotAvailable()
    {
        // given
        partitionManager.addMember(SOCKET_ADDRESS1);
        final StreamProcessorControl processorControl = rule.runStreamProcessor(this::buildStreamProcessor);

        rule.writeEvent(createTopic("foo", 1));

        // stream processor has processed CREATE and triggered partition creation
        waitUntil(() -> partitionEventsInState(PartitionState.CREATING).findFirst().isPresent());

        final long createPosition = partitionEventsInState(PartitionState.CREATING)
            .findFirst()
            .get()
            .getPosition();

        processorControl.close();

        // removing the CREATING event such that the stream processor reprocesses CREATE on restart
        rule.truncateLog(createPosition);
        processorControl.purgeSnapshot();

        // making a different cluster member available
        partitionManager.removeMember(SOCKET_ADDRESS1);
        partitionManager.addMember(SOCKET_ADDRESS2);

        // when restarting the stream processor
        processorControl.start();

        // then
        waitUntil(() -> partitionEventsInState(PartitionState.CREATING).count() == 1);

        // it is not important for correct behavior in this scenario if
        // the request was resent (assuming that it the receiving broker is not available anyway),
        // so we don't assert the number of requests
        assertThat(partitionManager.getPartitionRequests()).extracting("endpoint").containsOnly(SOCKET_ADDRESS1);
    }

    @Test
    public void shouldPersistCreatingBrokerInPartitionCreateEvent()
    {
        // given
        partitionManager.addMember(SOCKET_ADDRESS1);
        final StreamProcessorControl processorControl = rule.runStreamProcessor(this::buildStreamProcessor);

        processorControl.blockAfterTopicEvent(e -> e.getValue().getState() == TopicState.CREATE);

        rule.writeEvent(createTopic("foo", 1));

        // when
        waitUntil(() -> partitionEventsInState(PartitionState.CREATE).findFirst().isPresent());

        // then
        final PartitionEvent partitionEvent = partitionEventsInState(PartitionState.CREATE).findFirst().get().getValue();
        final TopologyBroker creator = partitionEvent.getCreator();
        assertThat(creator.getHost()).isEqualTo(SOCKET_ADDRESS1.getHostBuffer());
        assertThat(creator.getPort()).isEqualTo(SOCKET_ADDRESS1.port());
    }

    @Test
    public void shouldNotSendResponseTwiceOnInterleavingPartitionCompletion()
    {
        // given
        partitionManager.addMember(SOCKET_ADDRESS1);

        final StreamProcessorControl processorControl = rule.runStreamProcessor(this::buildStreamProcessor);

        // wait after partition CREATE events have been written
        processorControl.blockAfterTopicEvent(e -> e.getValue().getState() == TopicState.CREATE);

        rule.writeEvent(createTopic("foo", 2));

        waitUntil(() -> partitionEventsInState(PartitionState.CREATE).count() == 2);
        final TypedEvent<PartitionEvent> secondCreateEvent = rule.events()
            .onlyPartitionEvents()
            .skip(1)
            .findFirst()
            .get();

        // wait after create event has been processed (=> and added to partition index singalling a pending partition)
        processorControl.blockAfterEvent(e -> e.getPosition() == secondCreateEvent.getPosition());
        processorControl.unblock();
        waitUntil(() -> processorControl.isBlocked());

        // when both partitions become available at once
        final List<PartitionRequest> requests = partitionManager.getPartitionRequests();
        partitionManager.declarePartitionLeader(SOCKET_ADDRESS1, requests.get(0).getPartitionId());
        partitionManager.declarePartitionLeader(SOCKET_ADDRESS1, requests.get(1).getPartitionId());
        streamProcessor.runAsync(checkPartitionsCmd);
        waitUntil(() -> partitionEventsInState(PartitionState.CREATE_COMPLETE).findFirst().isPresent());

        // then the topic created response is sent once
        processorControl.unblock();
        waitUntil(() -> topicEventsInState(TopicState.CREATED).findFirst().isPresent());
        assertThat(rule.getOutput().getSentResponses()).hasSize(1);
    }

    @Test
    public void shouldDistributePartitionsRoundRobin()
    {
        // given
        partitionManager.addMember(SOCKET_ADDRESS1);
        partitionManager.addMember(SOCKET_ADDRESS2);

        rule.runStreamProcessor(this::buildStreamProcessor);

        // when
        rule.writeEvent(createTopic("foo", 4));
        waitUntil(() -> partitionEventsInState(PartitionState.CREATING).count() == 4);

        // then
        final List<PartitionRequest> requests = partitionManager.getPartitionRequests();
        assertThat(requests).extracting(r -> r.endpoint).containsOnly(
                SOCKET_ADDRESS1, SOCKET_ADDRESS2, SOCKET_ADDRESS1, SOCKET_ADDRESS2);
    }

    @Test
    public void shouldDistributePartitionsRoundRobinWhenCreatingMultipleTopics()
    {
        // given
        partitionManager.addMember(SOCKET_ADDRESS1);
        partitionManager.addMember(SOCKET_ADDRESS2);

        rule.runStreamProcessor(this::buildStreamProcessor);

        // creating a first partition
        rule.writeEvent(createTopic("foo", 1));
        waitUntil(() -> partitionEventsInState(PartitionState.CREATING).count() == 1);
        final SocketAddress firstPartitionCreator = partitionManager.getPartitionRequests().get(0).endpoint;

        // when creating a second partition
        rule.writeEvent(createTopic("bar", 1));
        waitUntil(() -> partitionEventsInState(PartitionState.CREATING).count() == 2);

        // then round-robin distribution continues
        final SocketAddress secondPartitionCreator = partitionManager.getPartitionRequests().get(1).endpoint;
        assertThat(secondPartitionCreator).isNotEqualTo(firstPartitionCreator);
    }

    @Test
    public void shouldSkipMembersWithoutManagmenetApiWhenDistributePartitionsRoundRobin()
    {
        // given
        partitionManager.addMember(SOCKET_ADDRESS1);
        partitionManager.addMember(SOCKET_ADDRESS2);
        // last member has no management endpoint set yet
        partitionManager.currentMembers.add(new Member()
        {
            @Override
            public SocketAddress getManagementAddress()
            {
                return null;
            }

            @Override
            public IntIterator getLeadingPartitions()
            {
                return null;
            }
        });

        rule.runStreamProcessor(this::buildStreamProcessor);

        // when
        rule.writeEvent(createTopic("foo", 4));
        waitUntil(() -> partitionEventsInState(PartitionState.CREATING).count() == 4);

        // then
        final List<PartitionRequest> requests = partitionManager.getPartitionRequests();
        assertThat(requests).extracting(r -> r.endpoint).containsOnly(
            SOCKET_ADDRESS1, SOCKET_ADDRESS2, SOCKET_ADDRESS1, SOCKET_ADDRESS2);
    }

    @Test
    public void shouldCreateNewPartitionOnExpiration()
    {
        // given
        rule.getClock().pinCurrentTime();

        partitionManager.addMember(SOCKET_ADDRESS1);
        partitionManager.addMember(SOCKET_ADDRESS2);

        rule.runStreamProcessor(this::buildStreamProcessor);

        rule.writeEvent(createTopic("foo", 2));
        waitUntil(() -> partitionEventsInState(PartitionState.CREATING).count() == 2);

        // when
        rule.getClock().addTime(CREATION_EXPIRATION.plusSeconds(1));
        streamProcessor.runAsync(checkPartitionsCmd);
        waitUntil(() -> partitionEventsInState(PartitionState.CREATE).count() == 4);

        // then
        assertThat(partitionEventsInState(PartitionState.CREATE).count()).isEqualTo(4);

        final List<TypedEvent<PartitionEvent>> creationEvents =
                partitionEventsInState(PartitionState.CREATE).collect(Collectors.toList());
        assertThat(creationEvents).extracting("value.id").doesNotHaveDuplicates();

        final List<PartitionRequest> requests = partitionManager.getPartitionRequests();
        assertThat(requests).extracting(r -> r.endpoint).containsOnly(
                SOCKET_ADDRESS1, SOCKET_ADDRESS2, SOCKET_ADDRESS1, SOCKET_ADDRESS2);
    }

    @Test
    public void shouldSetCreationExpirationTimeInEvent()
    {
        // given
        rule.getClock().pinCurrentTime();

        partitionManager.addMember(SOCKET_ADDRESS1);

        rule.runStreamProcessor(this::buildStreamProcessor);

        // when
        rule.writeEvent(createTopic("foo", 1));
        waitUntil(() -> partitionEventsInState(PartitionState.CREATING).count() == 1);

        // then
        final PartitionEvent creatingEvent = partitionEventsInState(PartitionState.CREATING).findFirst().get().getValue();
        final Instant expectedExpirationTime = rule.getClock().getCurrentTime().plus(CREATION_EXPIRATION);
        assertThat(creatingEvent.getCreationTimeout()).isEqualTo(expectedExpirationTime.toEpochMilli());
    }

    @Test
    public void shouldSendResponseAfterTheDefinedNumberOfPartitionsIsCreated()
    {
        // given
        rule.getClock().pinCurrentTime();

        partitionManager.addMember(SOCKET_ADDRESS1);

        rule.runStreamProcessor(this::buildStreamProcessor);

        // request for two partitions
        rule.writeEvent(createTopic("foo", 2));
        waitUntil(() -> partitionEventsInState(PartitionState.CREATING).count() == 2);

        final PartitionRequest firstRequest = partitionManager.getPartitionRequests().get(0);
        partitionManager.declarePartitionLeader(SOCKET_ADDRESS1, firstRequest.getPartitionId());

        // a partition with expired creation
        rule.getClock().addTime(CREATION_EXPIRATION.plusSeconds(1));
        streamProcessor.runAsync(checkPartitionsCmd);
        waitUntil(() -> partitionEventsInState(PartitionState.CREATING).count() == 3);

        final PartitionEvent creatingEvent = partitionEventsInState(PartitionState.CREATING)
            .skip(2)
            .findFirst()
            .get()
            .getValue();

        // when
        partitionManager.declarePartitionLeader(SOCKET_ADDRESS1, creatingEvent.getId());
        streamProcessor.runAsync(checkPartitionsCmd);
        waitUntil(() -> topicEventsInState(TopicState.CREATED).findFirst().isPresent());

        // then topic is marked created after two out of three partitions have been created
        assertThat(topicEventsInState(TopicState.CREATED).count()).isEqualTo(1);
        assertThat(rule.getOutput().getSentResponses()).hasSize(1);
    }

    @Test
    public void shouldTriggerExpirationOnlyOnce() throws InterruptedException
    {
        // given
        rule.getClock().pinCurrentTime();
        partitionManager.addMember(SOCKET_ADDRESS1);

        rule.runStreamProcessor(this::buildStreamProcessor);

        rule.writeEvent(createTopic("foo", 1));
        waitUntil(() -> partitionEventsInState(PartitionState.CREATING).count() == 1);

        // creation expires
        rule.getClock().addTime(CREATION_EXPIRATION.plusSeconds(1));
        streamProcessor.runAsync(checkPartitionsCmd);
        waitUntil(() -> partitionEventsInState(PartitionState.CREATING).count() == 2);

        // when checking expiration again
        streamProcessor.runAsync(checkPartitionsCmd);

        // then this does not result in a third partition
        Thread.sleep(500L);
        assertThat(partitionEventsInState(PartitionState.CREATING).count()).isEqualTo(2);
    }

    @Test
    public void shouldRejectSecondExpirationCommand()
    {
        // given
        rule.getClock().pinCurrentTime();
        partitionManager.addMember(SOCKET_ADDRESS1);

        final StreamProcessorControl processorControl = rule.runStreamProcessor(this::buildStreamProcessor);
        processorControl.blockAfterPartitionEvent(e -> e.getValue().getState() == PartitionState.CREATING);

        rule.writeEvent(createTopic("foo", 1));
        waitUntil(() -> partitionEventsInState(PartitionState.CREATING).count() == 1);

        // creation expires once
        rule.getClock().addTime(CREATION_EXPIRATION.plusSeconds(1));
        streamProcessor.runAsync(checkPartitionsCmd);

        // when the expiration check is run a second time before the stream processor handles the first command
        streamProcessor.runAsync(checkPartitionsCmd);

        // then there is only one expiration event and one new partition create event
        processorControl.unblock();
        waitUntil(() -> partitionEventsInState(PartitionState.CREATING).count() == 2);

        final List<TypedEvent<PartitionEvent>> partitionEvents = rule.events()
            .onlyPartitionEvents()
            .collect(Collectors.toList());

        assertThat(partitionEvents).extracting("value.state")
            .containsExactly(
                    PartitionState.CREATE,
                    PartitionState.CREATING,
                    PartitionState.CREATE_EXPIRE,
                    PartitionState.CREATE_EXPIRE,
                    PartitionState.CREATE_EXPIRED,
                    PartitionState.CREATE,
                    PartitionState.CREATE_EXPIRE_REJECTED,
                    PartitionState.CREATING);
    }

    @Test
    public void shouldRejectExpirationOnIntermittentCompletion()
    {
        rule.getClock().pinCurrentTime();
        partitionManager.addMember(SOCKET_ADDRESS1);

        final StreamProcessorControl processorControl = rule.runStreamProcessor(this::buildStreamProcessor);
        processorControl.blockAfterPartitionEvent(e -> e.getValue().getState() == PartitionState.CREATING);

        rule.writeEvent(createTopic("foo", 1));
        waitUntil(() -> processorControl.isBlocked());

        final PartitionRequest request = partitionManager.getPartitionRequests().get(0);
        partitionManager.declarePartitionLeader(SOCKET_ADDRESS1, request.getPartitionId());

        // when creating a complete and expire command
        rule.getClock().addTime(CREATION_EXPIRATION.plusSeconds(1));
        streamProcessor.runAsync(checkPartitionsCmd);

        // then
        processorControl.unblock();
        waitUntil(() -> partitionEventsInState(PartitionState.CREATE_EXPIRE_REJECTED).count() == 1);
        assertThat(partitionEventsInState(PartitionState.CREATE_EXPIRED).count()).isEqualTo(0);
        assertThat(partitionEventsInState(PartitionState.CREATED).count()).isEqualTo(1);
    }

    @Test
    public void shouldCreateTopicsWithInterleavingRequests()
    {
        // given
        partitionManager.addMember(SOCKET_ADDRESS1);

        final long request1 = 42;
        final long request2 = 52;

        rule.newEvent()
            .event(createTopic("foo", 2))
            .metadata(m -> m.requestId(request1))
            .write();

        rule.newEvent()
            .event(createTopic("bar", 2))
            .metadata(m -> m.requestId(request2))
            .write();

        rule.runStreamProcessor(this::buildStreamProcessor);

        waitUntil(() -> partitionManager.getPartitionRequests().size() == 4);

        partitionManager.getPartitionRequests().forEach(r -> partitionManager.declarePartitionLeader(SOCKET_ADDRESS1, r.getPartitionId()));

        // when
        streamProcessor.runAsync(checkPartitionsCmd);

        // then
        waitUntil(() -> rule.getOutput().getSentResponses().size() >= 2);

        final RequestResponseHeaderDescriptor responseHeader = new RequestResponseHeaderDescriptor();

        final List<Long> respondedRequests = rule.getOutput().getSentResponses().stream()
            .map(b -> responseHeader.wrap(b, TransportHeaderDescriptor.headerLength()).requestId())
            .collect(Collectors.toList());

        assertThat(respondedRequests).containsExactlyInAnyOrder(request1, request2);
    }

    @Test
    @Ignore("Requires fix for https://github.com/zeebe-io/zeebe/issues/478")
    public void shouldGenerateUniquePartitionIdsAfterRestartWithoutSnapshot()
    {
        // given
        final StreamProcessorControl processorControl = rule.runStreamProcessor(this::buildStreamProcessor);
        partitionManager.addMember(SOCKET_ADDRESS1);

        processorControl.blockAfterEvent(e ->
            Events.isTopicEvent(e) &&
            Events.asTopicEvent(e).getState() == TopicState.CREATE);
        processorControl.unblock();

        rule.writeEvent(createTopic("foo", 1));

        // stream processor has processed CREATE and triggered the cluster manager to create a partition
        waitUntil(() -> partitionEventsInState(PartitionState.CREATE).findFirst().isPresent());

        // when
        processorControl.close();
        processorControl.purgeSnapshot();

        processorControl.blockAfterEvent(e -> false);
        processorControl.start();

        rule.writeEvent(createTopic("bar", 1));

        // then
        waitUntil(() -> partitionEventsInState(PartitionState.CREATE).count() == 2);

        assertThat(partitionEventsInState(PartitionState.CREATE)).extracting("id").doesNotHaveDuplicates();
        fail("stronger assertion? e.g. that they are ascending?");
    }

    protected Stream<TypedEvent<PartitionEvent>> partitionEventsInState(PartitionState state)
    {
        return rule.events().onlyPartitionEvents().inState(state);
    }

    protected Stream<TypedEvent<TopicEvent>> topicEventsInState(TopicState state)
    {
        return rule.events().onlyTopicEvents().inState(state);
    }

    protected TopicEvent createTopic(String name, int partitions)
    {
        final TopicEvent event = new TopicEvent();
        event.setName(BufferUtil.wrapString(name));
        event.setPartitions(partitions);
        event.setState(TopicState.CREATE);

        return event;
    }
}

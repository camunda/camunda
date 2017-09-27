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

import static io.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.agrona.DirectBuffer;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import io.zeebe.broker.clustering.management.Partition;
import io.zeebe.broker.clustering.management.PartitionManager;
import io.zeebe.broker.clustering.member.Member;
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
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.protocol.clientapi.EventType;
import io.zeebe.test.util.AutoCloseableRule;
import io.zeebe.transport.ServerOutput;
import io.zeebe.transport.SocketAddress;
import io.zeebe.util.actor.ActorScheduler;
import io.zeebe.util.actor.ActorSchedulerBuilder;
import io.zeebe.util.buffer.BufferUtil;
import io.zeebe.util.time.ClockUtil;

public class CreateTopicStreamProcessorTest
{

    public static final Duration CREATION_EXPIRATION = Duration.ofSeconds(60);

    public static final String STREAM_NAME = "stream";
    protected static final SocketAddress SOCKET_ADDRESS1 = new SocketAddress("saturn", 123);
    protected static final SocketAddress SOCKET_ADDRESS2 = new SocketAddress("mars", 456);

    public TemporaryFolder tempFolder = new TemporaryFolder();
    public AutoCloseableRule closeables = new AutoCloseableRule();

    @Rule
    public RuleChain ruleChain = RuleChain.outerRule(tempFolder).around(closeables);

    @Mock
    public ServerOutput output;

    public PartitionManagerImpl partitionManager;
    protected TestStreams streams;

    private TypedStreamProcessor streamProcessor;
    private ResolvePendingPartitionsCommand checkPartitionsCmd;

    @Before
    public void setUp()
    {
        MockitoAnnotations.initMocks(this);
        when(output.sendResponse(any())).thenReturn(true);

        final ActorScheduler scheduler = ActorSchedulerBuilder.createDefaultScheduler("foo");
        closeables.manage(scheduler);

        streams = new TestStreams(tempFolder.getRoot(), closeables, scheduler);
        final LogStream stream = streams.createLogStream(STREAM_NAME);

        this.partitionManager = new PartitionManagerImpl();

        final TopicsIndex topicsIndex = new TopicsIndex();
        final PendingPartitionsIndex partitionsIndex = new PendingPartitionsIndex();

        final TypedStreamEnvironment streamEnvironment = new TypedStreamEnvironment(stream, output)
            .withEventType(EventType.TOPIC_EVENT, TopicEvent.class)
            .withEventType(EventType.PARTITION_EVENT, PartitionEvent.class);

        streamEnvironment.buildStreamWriter();

        checkPartitionsCmd = new ResolvePendingPartitionsCommand(partitionsIndex, partitionManager, streamEnvironment.buildStreamWriter());

        streamProcessor = SystemPartitionManager.buildSystemStreamProcessor(
                streamEnvironment,
                partitionManager,
                topicsIndex,
                partitionsIndex,
                CREATION_EXPIRATION);
    }

    @After
    public void reset()
    {
        ClockUtil.reset();
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

        final StreamProcessorControl processorControl = streams.runStreamProcessor(STREAM_NAME, streamProcessor);

        processorControl.blockAfterEvent(e ->
            Events.isPartitionEvent(e) &&
            Events.asPartitionEvent(e).getState() == PartitionState.CREATING);
        processorControl.unblock();

        // issuing create topic command
        streams.newEvent(STREAM_NAME)
            .event(createTopic("foo", 1))
            .write();

        // waiting for partition creating event => the stream processor is now suspended
        waitUntil(() -> partitionEventsInState(PartitionState.CREATING)
            .findFirst()
            .isPresent());

        partitionManager.declarePartitionLeader(SOCKET_ADDRESS1, "foo", 0);

        // calling check pending partition once
        streamProcessor.runAsync(checkPartitionsCmd);

        // waiting for partition creation complete command
        waitUntil(() -> partitionEventsInState(PartitionState.CREATE_COMPLETE)
            .findFirst()
            .isPresent());

        // when
        // calling check pending partition again
        streamProcessor.runAsync(checkPartitionsCmd);

        // waiting for partition creation complete command
        waitUntil(() -> partitionEventsInState(PartitionState.CREATE_COMPLETE)
            .count() == 2);

        // and resuming stream processing
        processorControl.unblock();

        // then
        waitUntil(() -> partitionEventsInState(PartitionState.CREATE_COMPLETE_REJECTED)
            .findFirst()
            .isPresent());

        final List<PartitionEvent> partitionEvents = streams.events(STREAM_NAME)
            .filter(Events::isPartitionEvent)
            .map(Events::asPartitionEvent)
            .collect(Collectors.toList());

        assertThat(partitionEvents).extracting("state")
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

        final StreamProcessorControl processorControl = streams.runStreamProcessor(STREAM_NAME, streamProcessor);
        processorControl.unblock();

        // a first topic is created
        final TopicEvent createTopicCommand = createTopic("foo", 1);
        streams.newEvent(STREAM_NAME)
            .event(createTopicCommand)
            .write();

        waitUntil(() -> partitionEventsInState(PartitionState.CREATING).findFirst().isPresent());
        partitionManager.declarePartitionLeader(SOCKET_ADDRESS1, "foo", 0);
        streamProcessor.runAsync(checkPartitionsCmd);

        waitUntil(() -> topicEventsInState(TopicState.CREATED).findFirst().isPresent());

        // when creating the same topic again
        streams.newEvent(STREAM_NAME)
            .event(createTopicCommand)
            .write();

        // then
        waitUntil(() -> topicEventsInState(TopicState.CREATE_REJECTED).findFirst().isPresent());

        // only the first create topic command resulted in a partition
        assertThat(partitionEventsInState(PartitionState.CREATE).count()).isEqualTo(1);
    }

    @Test
    @Ignore("https://github.com/zeebe-io/zeebe/issues/415")
    public void shouldNotResendPartitionRequestOnRecovery() throws InterruptedException
    {
        // given
        final StreamProcessorControl processorControl = streams.runStreamProcessor(STREAM_NAME, streamProcessor);

        processorControl.blockAfterEvent(e ->
            Events.isTopicEvent(e) &&
            Events.asTopicEvent(e).getState() == TopicState.CREATE);
        processorControl.unblock();

        streams.newEvent(STREAM_NAME)
            .event(createTopic("foo", 1))
            .write();

        // stream processor has processed CREATE and triggered the cluster manager to create a partition
        waitUntil(() -> partitionEventsInState(PartitionState.CREATE).findFirst().isPresent());

        // when restarting the stream processor
        processorControl.close();
        processorControl.start();
        processorControl.unblock();

        // then the same partition has not been created after restart
        // (because we cannot be sure the partition has not been created before yet)
        Thread.sleep(500L); // not explicity condition we can wait for
        assertThat(partitionManager.getPartitionRequests()).isEqualTo(0);
    }

    @Test
    public void shouldNotSendResponseTwiceOnInterleavingPartitionCompletion()
    {
        // given
        partitionManager.addMember(SOCKET_ADDRESS1);

        final StreamProcessorControl processorControl = streams.runStreamProcessor(STREAM_NAME, streamProcessor);

        // wait after partition CREATE events have been written
        processorControl.blockAfterEvent(e ->
            Events.isTopicEvent(e) &&
            Events.asTopicEvent(e).getState() == TopicState.CREATE);
        processorControl.unblock();

        streams.newEvent(STREAM_NAME)
            .event(createTopic("foo", 2))
            .write();

        waitUntil(() -> partitionEventsInState(PartitionState.CREATE).count() == 2);
        final LoggedEvent secondCreateEvent = streams.events(STREAM_NAME)
            .filter(e -> Events.isPartitionEvent(e))
            .skip(1)
            .findFirst()
            .get();

        // wait after create event has been processed (=> and added to partition index singalling a pending partition)
        processorControl.blockAfterEvent(e -> e.getPosition() == secondCreateEvent.getPosition());
        processorControl.unblock();
        waitUntil(() -> processorControl.isBlocked());

        // when both partitions become available at once
        partitionManager.declarePartitionLeader(SOCKET_ADDRESS1, "foo", 0);
        partitionManager.declarePartitionLeader(SOCKET_ADDRESS1, "foo", 1);
        streamProcessor.runAsync(checkPartitionsCmd);
        waitUntil(() -> partitionEventsInState(PartitionState.CREATE_COMPLETE).findFirst().isPresent());

        // then the topic created response is sent once
        processorControl.unblock();
        waitUntil(() -> topicEventsInState(TopicState.CREATED).findFirst().isPresent());
        verify(output, times(1)).sendResponse(any());
    }

    @Test
    public void shouldDistributePartitionsRoundRobin()
    {
        // given
        partitionManager.addMember(SOCKET_ADDRESS1);
        partitionManager.addMember(SOCKET_ADDRESS2);

        final StreamProcessorControl processorControl = streams.runStreamProcessor(STREAM_NAME, streamProcessor);
        processorControl.unblock();

        // when
        streams.newEvent(STREAM_NAME)
            .event(createTopic("foo", 4))
            .write();
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

        final StreamProcessorControl processorControl = streams.runStreamProcessor(STREAM_NAME, streamProcessor);
        processorControl.unblock();

        // creating a first partition
        streams.newEvent(STREAM_NAME)
            .event(createTopic("foo", 1))
            .write();
        waitUntil(() -> partitionEventsInState(PartitionState.CREATING).count() == 1);
        final SocketAddress firstPartitionCreator = partitionManager.getPartitionRequests().get(0).endpoint;

        // when creating a second partition
        streams.newEvent(STREAM_NAME)
            .event(createTopic("bar", 1))
            .write();
        waitUntil(() -> partitionEventsInState(PartitionState.CREATING).count() == 2);

        // then round-robin distribution continues
        final SocketAddress secondPartitionCreator = partitionManager.getPartitionRequests().get(1).endpoint;
        assertThat(secondPartitionCreator).isNotEqualTo(firstPartitionCreator);

    }

    @Test
    public void shouldCreateNewPartitionOnExpiration()
    {
        // given
        ClockUtil.pinCurrentTime();

        partitionManager.addMember(SOCKET_ADDRESS1);
        partitionManager.addMember(SOCKET_ADDRESS2);

        final StreamProcessorControl processorControl = streams.runStreamProcessor(STREAM_NAME, streamProcessor);
        processorControl.unblock();

        streams.newEvent(STREAM_NAME)
            .event(createTopic("foo", 2))
            .write();
        waitUntil(() -> partitionEventsInState(PartitionState.CREATING).count() == 2);

        // when
        ClockUtil.addTime(CREATION_EXPIRATION.plusSeconds(1));
        streamProcessor.runAsync(checkPartitionsCmd);
        waitUntil(() -> partitionEventsInState(PartitionState.CREATE).count() == 4);

        // then
        assertThat(partitionEventsInState(PartitionState.CREATE).count()).isEqualTo(4);

        final List<PartitionEvent> creationEvents = partitionEventsInState(PartitionState.CREATE).collect(Collectors.toList());
        assertThat(creationEvents).extracting("id").doesNotHaveDuplicates();

        final List<PartitionRequest> requests = partitionManager.getPartitionRequests();
        assertThat(requests).extracting(r -> r.endpoint).containsOnly(
                SOCKET_ADDRESS1, SOCKET_ADDRESS2, SOCKET_ADDRESS1, SOCKET_ADDRESS2);
    }

    @Test
    public void shouldSetCreationExpirationTimeInEvent()
    {
        // given
        ClockUtil.pinCurrentTime();

        partitionManager.addMember(SOCKET_ADDRESS1);

        final StreamProcessorControl processorControl = streams.runStreamProcessor(STREAM_NAME, streamProcessor);
        processorControl.unblock();

        // when
        streams.newEvent(STREAM_NAME)
            .event(createTopic("foo", 1))
            .write();
        waitUntil(() -> partitionEventsInState(PartitionState.CREATING).count() == 1);

        // then
        final PartitionEvent creatingEvent = partitionEventsInState(PartitionState.CREATING).findFirst().get();
        final Instant expectedExpirationTime = ClockUtil.getCurrentTime().plus(CREATION_EXPIRATION);
        assertThat(creatingEvent.getCreationTimeout()).isEqualTo(expectedExpirationTime.toEpochMilli());
    }

    @Test
    public void shouldSendResponseAfterTheDefinedNumberOfPartitionsIsCreated()
    {
        // given
        ClockUtil.pinCurrentTime();

        partitionManager.addMember(SOCKET_ADDRESS1);

        final StreamProcessorControl processorControl = streams.runStreamProcessor(STREAM_NAME, streamProcessor);
        processorControl.unblock();

        // request for two partitions
        streams.newEvent(STREAM_NAME)
            .event(createTopic("foo", 2))
            .write();
        waitUntil(() -> partitionEventsInState(PartitionState.CREATING).count() == 2);

        partitionManager.declarePartitionLeader(SOCKET_ADDRESS1, "foo", 0);

        // a partition with expired creation
        ClockUtil.addTime(CREATION_EXPIRATION.plusSeconds(1));
        streamProcessor.runAsync(checkPartitionsCmd);
        waitUntil(() -> partitionEventsInState(PartitionState.CREATING).count() == 3);

        final PartitionEvent event = partitionEventsInState(PartitionState.CREATING).skip(2).findFirst().get();

        // when
        partitionManager.declarePartitionLeader(SOCKET_ADDRESS1, "foo", event.getId());
        streamProcessor.runAsync(checkPartitionsCmd);
        waitUntil(() -> topicEventsInState(TopicState.CREATED).findFirst().isPresent());

        // then topic is marked created after two out of three partitions have been created
        assertThat(topicEventsInState(TopicState.CREATED).count()).isEqualTo(1);
        verify(output, times(1)).sendResponse(any());
    }

    @Test
    public void shouldTriggerExpirationOnlyOnce() throws InterruptedException
    {
        // given
        ClockUtil.pinCurrentTime();
        partitionManager.addMember(SOCKET_ADDRESS1);

        final StreamProcessorControl processorControl = streams.runStreamProcessor(STREAM_NAME, streamProcessor);
        processorControl.unblock();

        streams.newEvent(STREAM_NAME)
            .event(createTopic("foo", 1))
            .write();
        waitUntil(() -> partitionEventsInState(PartitionState.CREATING).count() == 1);

        // creation expires
        ClockUtil.addTime(CREATION_EXPIRATION.plusSeconds(1));
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
        ClockUtil.pinCurrentTime();
        partitionManager.addMember(SOCKET_ADDRESS1);

        final StreamProcessorControl processorControl = streams.runStreamProcessor(STREAM_NAME, streamProcessor);
        processorControl.blockAfterEvent(e ->
            Events.isPartitionEvent(e) &&
            Events.asPartitionEvent(e).getState() == PartitionState.CREATING);
        processorControl.unblock();

        streams.newEvent(STREAM_NAME)
            .event(createTopic("foo", 1))
            .write();
        waitUntil(() -> partitionEventsInState(PartitionState.CREATING).count() == 1);

        // creation expires once
        ClockUtil.addTime(CREATION_EXPIRATION.plusSeconds(1));
        streamProcessor.runAsync(checkPartitionsCmd);

        // when the expiration check is run a second time before the stream processor handles the first command
        streamProcessor.runAsync(checkPartitionsCmd);

        // then there is only one expiration event and one new partition create event
        processorControl.unblock();
        waitUntil(() -> partitionEventsInState(PartitionState.CREATING).count() == 2);

        final List<PartitionEvent> partitionEvents = streams.events(STREAM_NAME)
            .filter(Events::isPartitionEvent)
            .map(Events::asPartitionEvent)
            .collect(Collectors.toList());

        assertThat(partitionEvents).extracting("state")
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
        ClockUtil.pinCurrentTime();
        partitionManager.addMember(SOCKET_ADDRESS1);

        final StreamProcessorControl processorControl = streams.runStreamProcessor(STREAM_NAME, streamProcessor);
        processorControl.blockAfterEvent(e ->
            Events.isPartitionEvent(e) &&
            Events.asPartitionEvent(e).getState() == PartitionState.CREATING);
        processorControl.unblock();

        streams.newEvent(STREAM_NAME)
            .event(createTopic("foo", 1))
            .write();
        waitUntil(() -> processorControl.isBlocked());

        partitionManager.declarePartitionLeader(SOCKET_ADDRESS1, "foo", 0);

        // when creating a complete and expire command
        ClockUtil.addTime(CREATION_EXPIRATION.plusSeconds(1));
        streamProcessor.runAsync(checkPartitionsCmd);

        // then
        processorControl.unblock();
        waitUntil(() -> partitionEventsInState(PartitionState.CREATE_EXPIRE_REJECTED).count() == 1);
        assertThat(partitionEventsInState(PartitionState.CREATE_EXPIRED).count()).isEqualTo(0);
        assertThat(partitionEventsInState(PartitionState.CREATED).count()).isEqualTo(1);
    }

    protected Stream<PartitionEvent> partitionEventsInState(PartitionState state)
    {
        return streams.events(STREAM_NAME)
                .filter(Events::isPartitionEvent)
                .map(Events::asPartitionEvent)
                .filter(e -> e.getState() == state);
    }

    protected Stream<TopicEvent> topicEventsInState(TopicState state)
    {
        return streams.events(STREAM_NAME)
                .filter(Events::isTopicEvent)
                .map(Events::asTopicEvent)
                .filter(e -> e.getState() == state);
    }

    protected TopicEvent createTopic(String name, int partitions)
    {
        final TopicEvent event = new TopicEvent();
        event.setName(BufferUtil.wrapString(name));
        event.setPartitions(partitions);
        event.setState(TopicState.CREATE);

        return event;
    }

    protected class PartitionManagerImpl implements PartitionManager
    {

        protected List<PartitionRequest> partitionRequests = new CopyOnWriteArrayList<>();
        protected List<Member> currentMembers = new CopyOnWriteArrayList<>();
        protected Map<SocketAddress, List<Partition>> partitionsByMember = new HashMap<>();


        public void addMember(SocketAddress socketAddress)
        {
            this.currentMembers.add(new Member()
            {

                @Override
                public SocketAddress getManagementAddress()
                {
                    return socketAddress;
                }

                @Override
                public Iterator<Partition> getLeadingPartitions()
                {
                    return partitionsByMember.getOrDefault(socketAddress, Collections.emptyList()).iterator();
                }
            });
        }

        public void declarePartitionLeader(SocketAddress memberAddress, String topicName, int partitionId)
        {
            if (!this.partitionsByMember.containsKey(memberAddress))
            {
                this.partitionsByMember.put(memberAddress, new ArrayList<>());
            }

            this.partitionsByMember.get(memberAddress).add(new Partition()
            {
                @Override
                public DirectBuffer getTopicName()
                {
                    return BufferUtil.wrapString(topicName);
                }

                @Override
                public int getPartitionId()
                {
                    return partitionId;
                }
            });
        }

        @Override
        public boolean createPartitionRemote(SocketAddress remote, DirectBuffer topicName, int partitionId)
        {
            partitionRequests.add(new PartitionRequest(remote));
            return true;
        }

        public List<PartitionRequest> getPartitionRequests()
        {
            return partitionRequests;
        }

        @Override
        public Iterator<Member> getKnownMembers()
        {
            return currentMembers.iterator();
        }

    }

    protected class PartitionRequest
    {
        protected final SocketAddress endpoint = new SocketAddress();

        public PartitionRequest(SocketAddress endpoint)
        {
            this.endpoint.wrap(endpoint);
        }
    }

}

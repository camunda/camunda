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
package io.zeebe.broker.clustering.orchestration.topic;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;

import io.zeebe.broker.Loggers;
import io.zeebe.broker.clustering.api.CreatePartitionRequest;
import io.zeebe.broker.clustering.base.partitions.Partition;
import io.zeebe.broker.clustering.base.topology.NodeInfo;
import io.zeebe.broker.clustering.base.topology.PartitionInfo;
import io.zeebe.broker.clustering.base.topology.TopologyManager;
import io.zeebe.broker.clustering.base.topology.TopologyPartitionListener;
import io.zeebe.broker.clustering.orchestration.NodeSelector;
import io.zeebe.broker.clustering.orchestration.id.IdGenerator;
import io.zeebe.broker.clustering.orchestration.state.KnownTopics;
import io.zeebe.broker.clustering.orchestration.state.KnownTopicsListener;
import io.zeebe.broker.clustering.orchestration.state.TopicInfo;
import io.zeebe.broker.logstreams.processor.TypedStreamEnvironment;
import io.zeebe.broker.logstreams.processor.TypedStreamWriter;
import io.zeebe.msgpack.value.IntegerValue;
import io.zeebe.msgpack.value.ValueArray;
import io.zeebe.protocol.clientapi.Intent;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import io.zeebe.transport.ClientResponse;
import io.zeebe.transport.ClientTransport;
import io.zeebe.transport.RemoteAddress;
import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.future.ActorFuture;

public class TopicCreationService extends Actor implements Service<TopicCreationService>, KnownTopicsListener, TopologyPartitionListener
{
    private static final Logger LOG = Loggers.CLUSTERING_LOGGER;

    public static final Duration TIMER_RATE = Duration.ofSeconds(1);
    public static final Duration PENDING_TIMEOUT = Duration.ofMinutes(1);

    private final Injector<KnownTopics> stateInjector = new Injector<>();
    private final Injector<TopologyManager> topologyManagerInjector = new Injector<>();
    private final Injector<Partition> leaderSystemPartitionInjector = new Injector<>();
    private final Injector<IdGenerator> idGeneratorInjector = new Injector<>();
    private final Injector<NodeSelector> nodeOrchestratingServiceInjector = new Injector<>();
    private final Injector<ClientTransport> managementClientApiInjector = new Injector<>();

    private KnownTopics knownTopics;
    private TopologyManager topologyManager;
    private TypedStreamWriter streamWriter;
    private IdGenerator idGenerator;
    private NodeSelector nodeSelector;
    private ClientTransport clientTransport;

    private Set<String> pendingTopicCreationRequests = new HashSet<>();
    private Set<String> pendingTopicCompletions = new HashSet<>();

    @Override
    public void start(final ServiceStartContext startContext)
    {
        knownTopics = stateInjector.getValue();
        topologyManager = topologyManagerInjector.getValue();
        idGenerator = idGeneratorInjector.getValue();
        nodeSelector = nodeOrchestratingServiceInjector.getValue();
        clientTransport = managementClientApiInjector.getValue();

        final Partition leaderSystemPartition = leaderSystemPartitionInjector.getValue();
        final TypedStreamEnvironment typedStreamEnvironment = new TypedStreamEnvironment(leaderSystemPartition.getLogStream(), null);
        streamWriter = typedStreamEnvironment.buildStreamWriter();

        knownTopics.registerTopicListener(this);
        topologyManager.addTopologyPartitionListener(this);

        startContext.async(startContext.getScheduler().submitActor(this));
    }

    @Override
    public void stop(final ServiceStopContext stopContext)
    {
        stopContext.async(actor.close());
    }

    @Override
    public String getName()
    {
        return "create-topic";
    }

    @Override
    protected void onActorStarted()
    {
        actor.runAtFixedRate(TIMER_RATE, this::checkCurrentState);
    }

    @Override
    public void topicAdded(final String topicName)
    {
        // TODO(menski): limit to topic
        actor.run(this::checkCurrentState);
    }

    @Override
    public void onPartitionUpdated(final PartitionInfo partitionInfo, final NodeInfo member)
    {
        // TODO(menski): limit to topic
        actor.run(this::checkCurrentState);
    }

    private void checkCurrentState()
    {
        final ActorFuture<ClusterPartitionState> queryFuture = topologyManager.query(ClusterPartitionState::computeCurrentState);

        actor.runOnCompletion(queryFuture, (currentState, error) ->
        {
            if (error == null)
            {
                computeStateDifferences(currentState);
            }
            else
            {
                LOG.error("Unable to compute current cluster topic state from topology", error);
            }
        });
    }

    private void computeStateDifferences(final ClusterPartitionState currentState)
    {
        final ActorFuture<List<PendingTopic>> pendingTopicsFuture = knownTopics.queryTopics(topics -> computePendingTopics(topics, currentState));

        actor.runOnCompletion(pendingTopicsFuture, (pendingTopics, error) ->
        {
            if (error == null)
            {
                for (final PendingTopic pendingTopic : pendingTopics)
                {
                    final String topicName = pendingTopic.getTopicName();

                    if (pendingTopic.getMissingPartitions() > 0)
                    {
                        if (!pendingTopicCreationRequests.contains(topicName))
                        {
                            LOG.debug("Creating {} partitions for topic {}", pendingTopic.getMissingPartitions(), topicName);
                            for (int i = 0; i < pendingTopic.getMissingPartitions(); i++)
                            {
                                createPartition(pendingTopic);
                            }
                            pendingTopicCreationRequests.add(topicName);
                            actor.runDelayed(PENDING_TIMEOUT, () -> pendingTopicCreationRequests.remove(topicName));
                        }
                    }
                    else
                    {
                        if (!pendingTopicCompletions.contains(topicName))
                        {
                            final int partitionCount = pendingTopic.getPartitionCount();
                            final int replicationFactor = pendingTopic.getReplicationFactor();

                            final TopicRecord topicEvent = new TopicRecord();
                            topicEvent.setName(pendingTopic.getTopicNameBuffer());
                            topicEvent.setPartitions(partitionCount);
                            topicEvent.setReplicationFactor(replicationFactor);

                            final ValueArray<IntegerValue> eventPartitionIds = topicEvent.getPartitionIds();
                            pendingTopic.getPartitionIds().forEach(id -> eventPartitionIds.add().setValue(id));

                            actor.runUntilDone(() -> writeEvent(pendingTopic.getKey(), Intent.CREATE_COMPLETE, topicEvent));

                            pendingTopicCreationRequests.remove(topicName);
                            pendingTopicCompletions.add(topicName);
                            actor.runDelayed(PENDING_TIMEOUT, () -> pendingTopicCompletions.remove(topicName));
                            LOG.debug("Topic {} with {} partition(s) and replication factor {} created", topicName, partitionCount, replicationFactor);

                        }
                    }
                }
            }
            else
            {
                LOG.error("Failed to compute the topic partitions to create");
            }
        });
    }

    private List<PendingTopic> computePendingTopics(final Iterable<TopicInfo> topics, final ClusterPartitionState currentState)
    {
        final List<PendingTopic> pendingTopics = new ArrayList<>();

        for (final TopicInfo topic : topics)
        {
            if (topic.getPartitionIds().iterator().hasNext())
            {
                // topic already created
                continue;
            }

            final List<Integer> partitionIds = currentState.getPartitions(topic.getTopicNameBuffer())
                                                           .stream()
                                                           .map(PartitionNodes::getPartitionId)
                                                           .collect(Collectors.toList());

            final int missingPartitions = topic.getPartitionCount() - partitionIds.size();
            final PendingTopic pendingTopic = new PendingTopic(topic.getTopicNameBuffer(), topic.getPartitionCount(), topic.getReplicationFactor(), partitionIds, missingPartitions, topic.getKey());
            pendingTopics.add(pendingTopic);
        }

        return pendingTopics;
    }

    private void createPartition(final PendingTopic pendingTopic)
    {
        final ActorFuture<Integer> idFuture = idGenerator.nextId();
        actor.runOnCompletion(idFuture, (id, error) ->
        {
            if (error == null)
            {
                LOG.debug("Creating partition with id {} for topic {}", id, pendingTopic.getTopicName());
                sendCreatePartitionRequest(pendingTopic, id);
            }
            else
            {
                LOG.error("Failed to get new partition id for topic {}", pendingTopic.getTopicName(), error);
            }
        });
    }

    private void sendCreatePartitionRequest(final PendingTopic pendingTopic, final Integer partitionId)
    {
        final PartitionInfo newPartition = new PartitionInfo(pendingTopic.getTopicNameBuffer(), partitionId, pendingTopic.getReplicationFactor());
        final ActorFuture<NodeInfo> nextSocketAddressFuture = nodeSelector.getNextSocketAddress(newPartition);
        actor.runOnCompletion(nextSocketAddressFuture, (nodeInfo, error) ->
        {
            if (error == null)
            {
                LOG.debug("Send create partition request for topic {} to node {} with partition id {}", pendingTopic.getTopicName(), nodeInfo.getManagementApiAddress(), partitionId);
                sendCreatePartitionRequest(pendingTopic, partitionId, nodeInfo);
            }
            else
            {
                LOG.error("Problem in resolving next node address to create partition {} for topic {}", partitionId, pendingTopic.getTopicName(), error);
            }
        });

    }

    private void sendCreatePartitionRequest(final PendingTopic pendingTopic, final Integer partitionId, final NodeInfo nodeInfo)
    {
        final CreatePartitionRequest request = new CreatePartitionRequest()
            .topicName(pendingTopic.getTopicNameBuffer())
            .partitionId(partitionId)
            .replicationFactor(pendingTopic.getReplicationFactor());

        final RemoteAddress remoteAddress = clientTransport.registerRemoteAddress(nodeInfo.getManagementApiAddress());
        final ActorFuture<ClientResponse> responseFuture = clientTransport.getOutput().sendRequest(remoteAddress, request);

        actor.runOnCompletion(responseFuture, (createPartitionResponse, error) ->
        {
            if (error == null)
            {
                LOG.info("Partition {} for topic {} created on node {}", partitionId, pendingTopic.getTopicName(), nodeInfo.getManagementApiAddress());
            }
            else
            {
                LOG.warn("Failed to create partition {} for topic {} on node {}", partitionId, pendingTopic.getTopicName(), nodeInfo.getManagementApiAddress(), error);
            }
        });
    }

    private void writeEvent(final long key, Intent intent, final TopicRecord topicEvent)
    {
        if (streamWriter.writeFollowUpEvent(key, intent, topicEvent) >= 0)
        {
            actor.done();
        }
        else
        {
            actor.yield();
        }
    }

    @Override
    public TopicCreationService get()
    {
        return this;
    }

    public Injector<KnownTopics> getStateInjector()
    {
        return stateInjector;
    }

    public Injector<TopologyManager> getTopologyManagerInjector()
    {
        return topologyManagerInjector;
    }

    public Injector<Partition> getLeaderSystemPartitionInjector()
    {
        return leaderSystemPartitionInjector;
    }

    public Injector<IdGenerator> getIdGeneratorInjector()
    {
        return idGeneratorInjector;
    }

    public Injector<NodeSelector> getNodeOrchestratingServiceInjector()
    {
        return nodeOrchestratingServiceInjector;
    }

    public Injector<ClientTransport> getManagementClientApiInjector()
    {
        return managementClientApiInjector;
    }

}

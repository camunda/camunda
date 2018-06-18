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

import io.zeebe.broker.clustering.orchestration.state.KnownTopics;
import io.zeebe.broker.clustering.orchestration.state.TopicInfo;
import io.zeebe.broker.transport.controlmessage.AbstractControlMessageHandler;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.clientapi.ControlMessageType;
import io.zeebe.protocol.clientapi.ErrorCode;
import io.zeebe.protocol.impl.RecordMetadata;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import io.zeebe.transport.ServerOutput;
import io.zeebe.util.sched.ActorControl;
import io.zeebe.util.sched.future.ActorFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.agrona.DirectBuffer;

public class RequestPartitionsMessageHandler extends AbstractControlMessageHandler
    implements Service<RequestPartitionsMessageHandler> {

  private final Injector<KnownTopics> clusterTopicStateInjector = new Injector<>();

  private final AtomicReference<KnownTopics> clusterTopicStateReference = new AtomicReference<>();

  public RequestPartitionsMessageHandler(final ServerOutput serverOutput) {
    super(serverOutput);
  }

  @Override
  public RequestPartitionsMessageHandler get() {
    return this;
  }

  @Override
  public void start(final ServiceStartContext startContext) {
    final KnownTopics knownTopics = clusterTopicStateInjector.getValue();
    clusterTopicStateReference.set(knownTopics);
  }

  @Override
  public void stop(final ServiceStopContext stopContext) {
    clusterTopicStateReference.set(null);
  }

  @Override
  public ControlMessageType getMessageType() {
    return ControlMessageType.REQUEST_PARTITIONS;
  }

  @Override
  public void handle(
      final ActorControl actor,
      final int partitionId,
      final DirectBuffer buffer,
      final RecordMetadata metadata) {
    final int requestStreamId = metadata.getRequestStreamId();
    final long requestId = metadata.getRequestId();
    final KnownTopics knownTopics = clusterTopicStateReference.get();

    if (partitionId != Protocol.SYSTEM_PARTITION) {
      sendErrorResponse(
          actor,
          requestStreamId,
          requestId,
          "Partitions request must address the system partition %d",
          Protocol.SYSTEM_PARTITION);
    } else if (knownTopics == null) {
      // it is important that partition not found is returned here to signal a client that it may
      // have addressed a broker
      // that appeared as the system partition leader but is not (yet) able to respond
      sendErrorResponse(
          actor,
          requestStreamId,
          requestId,
          ErrorCode.PARTITION_NOT_FOUND,
          "Partitions request must address the leader of the system partition %d",
          Protocol.SYSTEM_PARTITION);
    } else {
      final ActorFuture<PartitionsResponse> responseFuture =
          knownTopics.queryTopics(this::createResponse);

      actor.runOnCompletion(
          responseFuture,
          (partitionsResponse, throwable) -> {
            if (throwable == null) {
              sendResponse(actor, requestStreamId, requestId, partitionsResponse);
            } else {
              // it is important that partition not found is returned here to signal a client that
              // it may have addressed a broker
              // that appeared as the system partition leader but is not (yet) able to respond
              sendErrorResponse(
                  actor,
                  requestStreamId,
                  requestId,
                  ErrorCode.PARTITION_NOT_FOUND,
                  throwable.getMessage());
            }
          });
    }
  }

  private PartitionsResponse createResponse(final Iterable<TopicInfo> topicInfos) {
    final PartitionsResponse response = new PartitionsResponse();

    for (final TopicInfo topicInfo : topicInfos) {
      final DirectBuffer topicName = topicInfo.getTopicNameBuffer();
      topicInfo
          .getPartitionIds()
          .iterator()
          .forEachRemaining(id -> response.addPartition(id.getValue(), topicName));
    }

    return response;
  }

  public Injector<KnownTopics> getClusterTopicStateInjector() {
    return clusterTopicStateInjector;
  }
}

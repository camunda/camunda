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
package io.zeebe.broker.system.management.topics;

import io.zeebe.broker.clustering.orchestration.state.KnownTopics;
import io.zeebe.broker.clustering.orchestration.state.TopicInfo;
import io.zeebe.transport.RemoteAddress;
import io.zeebe.transport.ServerOutput;
import io.zeebe.transport.ServerResponse;
import io.zeebe.util.sched.ActorControl;
import io.zeebe.util.sched.future.ActorFuture;
import org.agrona.DirectBuffer;
import org.agrona.collections.IntArrayList;

public class FetchCreatedTopicsRequestHandler {

  private final KnownTopics knownTopics;

  public FetchCreatedTopicsRequestHandler(KnownTopics knownTopics) {
    this.knownTopics = knownTopics;
  }

  public void onFetchCreatedTopics(
      DirectBuffer buffer,
      int offset,
      int length,
      ServerOutput output,
      RemoteAddress remoteAddress,
      long requestId,
      ActorControl actor) {

    final ActorFuture<FetchCreatedTopicsResponse> future =
        knownTopics.queryTopics(this::createResponse);

    actor.runOnCompletion(
        future,
        (response, err) -> {
          final ServerResponse serverResponse =
              new ServerResponse()
                  .writer(response)
                  .requestId(requestId)
                  .remoteAddress(remoteAddress);

          actor.runUntilDone(
              () -> {
                if (output.sendResponse(serverResponse)) {
                  actor.done();
                } else {
                  actor.yield();
                }
              });
        });
  }

  private FetchCreatedTopicsResponse createResponse(final Iterable<TopicInfo> topicInfos) {
    final FetchCreatedTopicsResponse response = new FetchCreatedTopicsResponse();

    for (final TopicInfo topicInfo : topicInfos) {

      final IntArrayList partitionIds = new IntArrayList();
      topicInfo.getPartitionIds().forEach(id -> partitionIds.addInt(id.getValue()));

      response.addTopic(topicInfo.getTopicName(), partitionIds);
    }

    return response;
  }
}

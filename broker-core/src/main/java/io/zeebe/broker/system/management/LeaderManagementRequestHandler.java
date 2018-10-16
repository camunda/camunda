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
package io.zeebe.broker.system.management;

import io.zeebe.broker.clustering.base.partitions.Partition;
import io.zeebe.broker.system.management.deployment.NotLeaderResponse;
import io.zeebe.broker.system.management.deployment.PushDeploymentRequestHandler;
import io.zeebe.clustering.management.MessageHeaderDecoder;
import io.zeebe.clustering.management.PushDeploymentRequestDecoder;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceGroupReference;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import io.zeebe.transport.BufferingServerTransport;
import io.zeebe.transport.RemoteAddress;
import io.zeebe.transport.ServerInputSubscription;
import io.zeebe.transport.ServerMessageHandler;
import io.zeebe.transport.ServerOutput;
import io.zeebe.transport.ServerRequestHandler;
import io.zeebe.transport.ServerResponse;
import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.future.ActorFuture;
import org.agrona.DirectBuffer;
import org.agrona.collections.Int2ObjectHashMap;

public class LeaderManagementRequestHandler extends Actor
    implements Service<LeaderManagementRequestHandler>, ServerRequestHandler, ServerMessageHandler {
  private final MessageHeaderDecoder messageHeaderDecoder = new MessageHeaderDecoder();

  private final Injector<BufferingServerTransport> managementApiServerTransportInjector =
      new Injector<>();
  private PushDeploymentRequestHandler pushDeploymentRequestHandler;

  private final ServiceGroupReference<Partition> leaderPartitionsGroupReference =
      ServiceGroupReference.<Partition>create()
          .onAdd((s, p) -> addPartition(p))
          .onRemove((s, p) -> removePartition(p))
          .build();

  private final Int2ObjectHashMap<Partition> leaderForPartitions = new Int2ObjectHashMap<>();

  private final ServerResponse response = new ServerResponse();
  private final NotLeaderResponse notLeaderResponse = new NotLeaderResponse();

  private BufferingServerTransport serverTransport;

  @Override
  public void start(final ServiceStartContext startContext) {
    serverTransport = managementApiServerTransportInjector.getValue();
    startContext.async(startContext.getScheduler().submitActor(this));
  }

  @Override
  public void stop(final ServiceStopContext stopContext) {
    stopContext.async(actor.close());
  }

  @Override
  protected void onActorStarting() {
    pushDeploymentRequestHandler = new PushDeploymentRequestHandler(leaderForPartitions, actor);

    final ActorFuture<ServerInputSubscription> subscriptionFuture =
        serverTransport.openSubscription("leader-management-request-handler", this, this);

    actor.runOnCompletion(
        subscriptionFuture,
        (subscription, err) -> {
          if (err != null) {
            throw new RuntimeException(err);
          } else {
            actor.consume(
                subscription,
                () -> {
                  if (subscription.poll() == 0) {
                    actor.yield();
                  }
                });
          }
        });
  }

  @Override
  public String getName() {
    return "management-request-handler";
  }

  @Override
  public boolean onMessage(
      final ServerOutput output,
      final RemoteAddress remoteAddress,
      final DirectBuffer buffer,
      final int offset,
      final int length) {
    // no messages currently supported
    return true;
  }

  @Override
  public boolean onRequest(
      final ServerOutput output,
      final RemoteAddress remoteAddress,
      final DirectBuffer buffer,
      final int offset,
      final int length,
      final long requestId) {
    messageHeaderDecoder.wrap(buffer, offset);

    final int schemaId = messageHeaderDecoder.schemaId();

    if (PushDeploymentRequestDecoder.SCHEMA_ID == schemaId) {
      final int templateId = messageHeaderDecoder.templateId();

      switch (templateId) {
        case PushDeploymentRequestDecoder.TEMPLATE_ID:
          {
            return onPushDeployment(buffer, offset, length, output, remoteAddress, requestId);
          }
        default:
          {
            // ignore
            return true;
          }
      }
    } else {
      // ignore
      return true;
    }
  }

  private boolean onPushDeployment(
      final DirectBuffer buffer,
      final int offset,
      final int length,
      final ServerOutput output,
      final RemoteAddress remoteAddress,
      final long requestId) {

    final boolean leader =
        pushDeploymentRequestHandler.onPushDeploymentRequest(
            output, remoteAddress, buffer, offset, length, requestId);

    if (!leader) {
      return sendNotLeaderResponse(output, remoteAddress, requestId);
    }
    return true;
  }

  private boolean sendNotLeaderResponse(
      final ServerOutput output, final RemoteAddress remoteAddress, final long requestId) {
    response
        .reset()
        .requestId(requestId)
        .remoteStreamId(remoteAddress.getStreamId())
        .writer(notLeaderResponse);

    return output.sendResponse(response);
  }

  @Override
  public LeaderManagementRequestHandler get() {
    return this;
  }

  public Injector<BufferingServerTransport> getManagementApiServerTransportInjector() {
    return managementApiServerTransportInjector;
  }

  private void addPartition(final Partition partition) {
    actor.submit(() -> leaderForPartitions.put(partition.getInfo().getPartitionId(), partition));
  }

  private void removePartition(final Partition partition) {
    actor.submit(() -> leaderForPartitions.remove(partition.getInfo().getPartitionId()));
  }

  public ServiceGroupReference<Partition> getLeaderPartitionsGroupReference() {
    return leaderPartitionsGroupReference;
  }
}

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
package io.zeebe.broker.system.workflow.repository.api.management;

import io.zeebe.clustering.management.FetchWorkflowRequestDecoder;
import io.zeebe.clustering.management.MessageHeaderDecoder;
import io.zeebe.servicecontainer.*;
import io.zeebe.transport.*;
import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.future.ActorFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.agrona.DirectBuffer;

public class DeploymentManagerRequestHandler extends Actor
    implements Service<DeploymentManagerRequestHandler>,
        ServerRequestHandler,
        ServerMessageHandler {
  private final MessageHeaderDecoder messageHeaderDecoder = new MessageHeaderDecoder();

  private final Injector<BufferingServerTransport> managementApiServerTransportInjector =
      new Injector<>();

  private final AtomicReference<FetchWorkflowRequestHandler> fetchWorkflowHandlerRef =
      new AtomicReference<>();

  private final ServerResponse response = new ServerResponse();
  private final NotLeaderResponse notLeaderResponse = new NotLeaderResponse();

  private BufferingServerTransport serverTransport;

  @Override
  public void start(ServiceStartContext startContext) {
    serverTransport = managementApiServerTransportInjector.getValue();
    startContext.async(startContext.getScheduler().submitActor(this));
  }

  @Override
  public void stop(ServiceStopContext stopContext) {
    stopContext.async(actor.close());
  }

  @Override
  protected void onActorStarting() {
    final ActorFuture<ServerInputSubscription> subscriptionFuture =
        serverTransport.openSubscription("deployment-manager", this, this);

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
  public boolean onMessage(
      ServerOutput output,
      RemoteAddress remoteAddress,
      DirectBuffer buffer,
      int offset,
      int length) {
    // no messages currently supported
    return true;
  }

  @Override
  public boolean onRequest(
      ServerOutput output,
      RemoteAddress remoteAddress,
      DirectBuffer buffer,
      int offset,
      int length,
      long requestId) {
    messageHeaderDecoder.wrap(buffer, offset);

    final int schemaId = messageHeaderDecoder.schemaId();

    if (FetchWorkflowRequestDecoder.SCHEMA_ID == schemaId) {
      final int templateId = messageHeaderDecoder.templateId();

      switch (templateId) {
        case FetchWorkflowRequestDecoder.TEMPLATE_ID:
          {
            return onFetchWorkflow(buffer, offset, length, output, remoteAddress, requestId);
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

  private boolean onFetchWorkflow(
      DirectBuffer buffer,
      int offset,
      int length,
      ServerOutput output,
      RemoteAddress remoteAddress,
      long requestId) {
    final FetchWorkflowRequestHandler handler = fetchWorkflowHandlerRef.get();

    if (handler != null) {
      handler.onFetchWorkflow(buffer, offset, length, output, remoteAddress, requestId, actor);

      return true;
    } else {
      response
          .reset()
          .requestId(requestId)
          .remoteStreamId(remoteAddress.getStreamId())
          .writer(notLeaderResponse);

      return output.sendResponse(response);
    }
  }

  @Override
  public DeploymentManagerRequestHandler get() {
    return this;
  }

  public Injector<BufferingServerTransport> getManagementApiServerTransportInjector() {
    return managementApiServerTransportInjector;
  }

  public void setFetchWorkflowRequestHandler(
      FetchWorkflowRequestHandler fetchWorkflowRequestHandler) {
    fetchWorkflowHandlerRef.set(fetchWorkflowRequestHandler);
  }
}

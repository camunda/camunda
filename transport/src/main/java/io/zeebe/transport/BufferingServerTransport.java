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
package io.zeebe.transport;

import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.transport.impl.TransportContext;
import io.zeebe.transport.impl.actor.ActorContext;
import io.zeebe.util.sched.future.ActorFuture;

public class BufferingServerTransport extends ServerTransport {
  protected final Dispatcher receiveBuffer;

  public BufferingServerTransport(
      ActorContext transportActorContext, TransportContext transportContext) {
    super(transportActorContext, transportContext);
    receiveBuffer = transportContext.getReceiveBuffer();
  }

  public ActorFuture<ServerInputSubscription> openSubscription(
      String subscriptionName,
      ServerMessageHandler messageHandler,
      ServerRequestHandler requestHandler) {
    return transportActorContext
        .getServerConductor()
        .openInputSubscription(
            subscriptionName,
            output,
            transportContext.getRemoteAddressList(),
            messageHandler,
            requestHandler);
  }
}

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
package io.zeebe.transport.impl.actor;

import io.zeebe.transport.impl.TransportChannel;
import io.zeebe.transport.impl.TransportContext;
import io.zeebe.transport.impl.selector.ReadTransportPoller;
import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.future.ActorFuture;

public class Receiver extends Actor {
  protected final ReadTransportPoller transportPoller;
  private String name;

  public Receiver(ActorContext actorContext, TransportContext context) {
    this.transportPoller = new ReadTransportPoller(actor);
    this.name = String.format("%s-receiver", context.getName());
    actorContext.setReceiver(this);
  }

  @Override
  protected void onActorStarted() {
    actor.runBlocking(transportPoller::pollBlocking, transportPoller::pollBlockingEnded);
  }

  @Override
  protected void onActorClosing() {
    transportPoller.close();
    transportPoller.clearChannels();
  }

  @Override
  public String getName() {
    return name;
  }

  public ActorFuture<Void> removeChannel(TransportChannel c) {
    return actor.call(
        () -> {
          transportPoller.removeChannel(c);
        });
  }

  public ActorFuture<Void> registerChannel(TransportChannel c) {
    return actor.call(
        () -> {
          transportPoller.addChannel(c);
        });
  }

  public ActorFuture<Void> close() {
    return actor.close();
  }
}

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

import io.zeebe.transport.impl.ServerSocketBinding;
import io.zeebe.transport.impl.TransportContext;
import io.zeebe.transport.impl.actor.ActorContext;
import io.zeebe.util.sched.future.ActorFuture;

public class ServerTransport implements AutoCloseable {
  protected final ServerOutput output;
  protected final ActorContext transportActorContext;
  protected final TransportContext transportContext;
  protected final ServerSocketBinding serverSocketBinding;

  public ServerTransport(ActorContext transportActorContext, TransportContext transportContext) {
    this.transportActorContext = transportActorContext;
    this.transportContext = transportContext;
    this.output = transportContext.getServerOutput();
    this.serverSocketBinding = transportContext.getServerSocketBinding();
  }

  /** @return interface to stage outbound data */
  public ServerOutput getOutput() {
    return output;
  }

  /**
   * Registers a listener with callbacks for whenever a connection to a remote gets established or
   * closed.
   */
  public ActorFuture<Void> registerChannelListener(TransportListener channelListener) {
    return transportActorContext.registerListener(channelListener);
  }

  public void removeChannelListener(TransportListener listener) {
    transportActorContext.removeListener(listener);
  }

  public ActorFuture<Void> closeAsync() {
    return transportActorContext.onClose();
  }

  public ActorFuture<Void> interruptAllChannels() {
    return transportActorContext.interruptAllChannels();
  }

  @Override
  public void close() {
    closeAsync().join();
  }

  public void releaseResources() {
    transportActorContext.getConductor().close().join();
  }
}

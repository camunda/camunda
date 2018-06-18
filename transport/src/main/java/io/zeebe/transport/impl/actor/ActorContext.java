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

import io.zeebe.transport.TransportListener;
import io.zeebe.transport.impl.sender.Sender;
import io.zeebe.util.metrics.MetricsManager;
import io.zeebe.util.sched.future.ActorFuture;

public abstract class ActorContext {
  private Conductor conductor;
  private Sender sender;
  private Receiver receiver;

  private MetricsManager metricsManager;

  public void setConductor(Conductor clientConductor) {
    this.conductor = clientConductor;
  }

  public void setReceiver(Receiver receiver) {
    this.receiver = receiver;
  }

  public void removeListener(TransportListener listener) {
    conductor.removeListener(listener);
  }

  public ActorFuture<Void> registerListener(TransportListener channelListener) {
    return conductor.registerListener(channelListener);
  }

  public ActorFuture<Void> onClose() {
    return conductor.close();
  }

  public ActorFuture<Void> closeAllOpenChannels() {
    return conductor.closeCurrentChannels();
  }

  public ActorFuture<Void> interruptAllChannels() {
    return conductor.interruptAllChannels();
  }

  public ActorFuture<Void> closeReceiver() {
    return receiver.close();
  }

  public Conductor getConductor() {
    return conductor;
  }

  public ClientConductor getClientConductor() {
    return (ClientConductor) conductor;
  }

  public ServerConductor getServerConductor() {
    return (ServerConductor) conductor;
  }

  public Receiver getReceiver() {
    return receiver;
  }

  public MetricsManager getMetricsManager() {
    return metricsManager;
  }

  public void setMetricsManager(MetricsManager metricsManager) {
    this.metricsManager = metricsManager;
  }

  public Sender getSender() {
    return sender;
  }

  public void setSender(Sender sender) {
    this.sender = sender;
  }

  public ActorFuture<Void> closeSender() {
    return sender.close();
  }
}

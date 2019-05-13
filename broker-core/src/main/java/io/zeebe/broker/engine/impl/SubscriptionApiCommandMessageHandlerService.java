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
package io.zeebe.broker.engine.impl;

import io.atomix.core.Atomix;
import io.zeebe.broker.clustering.base.partitions.Partition;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceGroupReference;
import io.zeebe.servicecontainer.ServiceName;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import io.zeebe.util.sched.Actor;
import org.agrona.collections.Int2ObjectHashMap;

public class SubscriptionApiCommandMessageHandlerService extends Actor
    implements Service<SubscriptionApiCommandMessageHandler> {

  private final Injector<Atomix> atomixInjector = new Injector<>();

  private final ServiceGroupReference<Partition> leaderPartitionsGroupReference =
      ServiceGroupReference.<Partition>create()
          .onAdd(this::addPartition)
          .onRemove(this::removePartition)
          .build();

  private final Int2ObjectHashMap<Partition> leaderPartitions = new Int2ObjectHashMap<>();

  private SubscriptionApiCommandMessageHandler messageHandler;
  private Atomix atomix;

  @Override
  public String getName() {
    return "subscription-api";
  }

  @Override
  public void start(ServiceStartContext context) {
    atomix = atomixInjector.getValue();
    context.async(context.getScheduler().submitActor(this, true));
  }

  @Override
  public void stop(ServiceStopContext stopContext) {
    stopContext.async(actor.close());
  }

  @Override
  protected void onActorStarting() {
    messageHandler = new SubscriptionApiCommandMessageHandler(actor, leaderPartitions);
    atomix.getCommunicationService().subscribe("subscription", messageHandler);
  }

  private void addPartition(final ServiceName<Partition> sericeName, final Partition partition) {
    actor.submit(() -> leaderPartitions.put(partition.getPartitionId(), partition));
  }

  private void removePartition(final ServiceName<Partition> sericeName, final Partition partition) {
    actor.submit(() -> leaderPartitions.remove(partition.getPartitionId()));
  }

  @Override
  public SubscriptionApiCommandMessageHandler get() {
    return messageHandler;
  }

  public ServiceGroupReference<Partition> getLeaderParitionsGroupReference() {
    return leaderPartitionsGroupReference;
  }

  public Injector<Atomix> getAtomixInjector() {
    return atomixInjector;
  }
}

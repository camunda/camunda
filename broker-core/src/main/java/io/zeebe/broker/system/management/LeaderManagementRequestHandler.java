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

import io.atomix.core.Atomix;
import io.zeebe.broker.clustering.base.partitions.Partition;
import io.zeebe.broker.system.management.deployment.PushDeploymentRequestHandler;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceGroupReference;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import io.zeebe.util.sched.Actor;
import org.agrona.collections.Int2ObjectHashMap;

public class LeaderManagementRequestHandler extends Actor
    implements Service<LeaderManagementRequestHandler> {

  private final Injector<Atomix> atomixInjector = new Injector<>();
  private PushDeploymentRequestHandler pushDeploymentRequestHandler;

  private final ServiceGroupReference<Partition> leaderPartitionsGroupReference =
      ServiceGroupReference.<Partition>create()
          .onAdd((s, p) -> addPartition(p))
          .onRemove((s, p) -> removePartition(p))
          .build();

  private final Int2ObjectHashMap<Partition> leaderForPartitions = new Int2ObjectHashMap<>();

  private Atomix atomix;

  @Override
  public void start(final ServiceStartContext startContext) {
    this.atomix = atomixInjector.getValue();
    startContext.async(startContext.getScheduler().submitActor(this));
  }

  @Override
  public void stop(final ServiceStopContext stopContext) {
    stopContext.async(actor.close());
  }

  @Override
  protected void onActorStarting() {
    pushDeploymentRequestHandler = new PushDeploymentRequestHandler(leaderForPartitions, actor);
    atomix.getCommunicationService().subscribe("deployment", pushDeploymentRequestHandler);
  }

  @Override
  public String getName() {
    return "management-request-handler";
  }

  @Override
  public LeaderManagementRequestHandler get() {
    return this;
  }

  private void addPartition(final Partition partition) {
    actor.submit(() -> leaderForPartitions.put(partition.getPartitionId(), partition));
  }

  private void removePartition(final Partition partition) {
    actor.submit(() -> leaderForPartitions.remove(partition.getPartitionId()));
  }

  public ServiceGroupReference<Partition> getLeaderPartitionsGroupReference() {
    return leaderPartitionsGroupReference;
  }

  public Injector<Atomix> getAtomixInjector() {
    return atomixInjector;
  }
}

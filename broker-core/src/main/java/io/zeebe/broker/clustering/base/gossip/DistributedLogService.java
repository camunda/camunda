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
package io.zeebe.broker.clustering.base.gossip;

import io.atomix.core.Atomix;
import io.atomix.protocols.raft.MultiRaftProtocol;
import io.zeebe.distributedlog.DistributedLogstream;
import io.zeebe.distributedlog.DistributedLogstreamBuilder;
import io.zeebe.distributedlog.DistributedLogstreamType;
import io.zeebe.distributedlog.impl.DistributedLogstreamConfig;
import io.zeebe.distributedlog.impl.DistributedLogstreamName;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.util.sched.future.CompletableActorFuture;
import java.util.concurrent.CompletableFuture;

public class DistributedLogService implements Service<Void> {

  private final Injector<Atomix> atomixInjector = new Injector<>();
  private Atomix atomix;

  private final String primitiveName = "distributed-log";

  private static final MultiRaftProtocol PROTOCOL =
      MultiRaftProtocol.builder()
          // Maps partitionName to partitionId
          .withPartitioner(DistributedLogstreamName.getInstance())
          .build();

  @Override
  public void start(ServiceStartContext startContext) {
    atomix = atomixInjector.getValue();

    final CompletableFuture<DistributedLogstream> distributedLogstreamCompletableFuture =
        atomix
            .<DistributedLogstreamBuilder, DistributedLogstreamConfig, DistributedLogstream>
                primitiveBuilder(primitiveName, DistributedLogstreamType.instance())
            .withProtocol(PROTOCOL)
            .buildAsync();

    final CompletableActorFuture<Void> startFuture = new CompletableActorFuture<>();

    distributedLogstreamCompletableFuture.thenAccept(
        log -> {
          startFuture.complete(null);
        });

    startContext.async(startFuture);
  }

  public Injector<Atomix> getAtomixInjector() {
    return atomixInjector;
  }

  @Override
  public Void get() {
    return null;
  }
}

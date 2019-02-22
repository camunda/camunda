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
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import java.util.concurrent.CompletableFuture;

public class AtomixJoinService implements Service<Void> {

  private Atomix atomix;
  private final Injector<Atomix> atomixInjector = new Injector<>();

  @Override
  public void start(ServiceStartContext startContext) {
    atomix = atomixInjector.getValue();

    final CompletableFuture<Void> startFuture = atomix.start();
    startContext.async(mapCompletableFuture(startFuture), true);
  }

  @Override
  public Void get() {
    return null;
  }

  private ActorFuture<Void> mapCompletableFuture(CompletableFuture<Void> atomixFuture) {
    final ActorFuture<Void> mappedActorFuture = new CompletableActorFuture<>();

    atomixFuture
        .thenAccept(mappedActorFuture::complete)
        .exceptionally(
            t -> {
              mappedActorFuture.completeExceptionally(t);
              return null;
            });
    return mappedActorFuture;
  }

  public Injector<Atomix> getAtomixInjector() {
    return atomixInjector;
  }
}

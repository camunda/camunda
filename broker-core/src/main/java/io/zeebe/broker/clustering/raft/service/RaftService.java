/**
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
package io.zeebe.broker.clustering.raft.service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import io.zeebe.broker.clustering.raft.Member;
import io.zeebe.broker.clustering.raft.MetaStore;
import io.zeebe.broker.clustering.raft.Raft;
import io.zeebe.broker.clustering.raft.RaftContext;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import io.zeebe.util.actor.ActorReference;
import io.zeebe.util.actor.ActorScheduler;

public class RaftService implements Service<Raft>
{
    private final LogStream logStream;
    private final List<Member> members;

    private Injector<ActorScheduler> actorSchedulerInjector = new Injector<>();
    private Injector<RaftContext> raftContextInjector = new Injector<>();

    private Raft raft;
    private MetaStore meta;
    private boolean bootstrap;
    private ActorReference actorRef;

    public RaftService(final LogStream logStream, final MetaStore meta, final List<Member> members, boolean bootstrap)
    {
        this.logStream = logStream;
        this.meta = meta;
        this.members = members;
        this.bootstrap = bootstrap;
    }

    @Override
    public void start(ServiceStartContext ctx)
    {
        ctx.run(() ->
        {
            final ActorScheduler actorScheduler = actorSchedulerInjector.getValue();
            final RaftContext raftContext = raftContextInjector.getValue();

            raft = new Raft(raftContext, logStream, meta);

            if (bootstrap)
            {
                raft.bootstrap();
            }
            else
            {
                raft.join(members);
            }

            actorRef = actorScheduler.schedule(raft);
        });
    }

    @Override
    public void stop(ServiceStopContext ctx)
    {
        final CompletableFuture<Void> closeFuture = raft.closeAsync()
                                                        .thenCompose((v) ->
                                                        {
                                                            actorRef.close();
                                                            return raft.stream().closeAsync();
                                                        });

        ctx.async(closeFuture);

    }

    @Override
    public Raft get()
    {
        return raft;
    }

    public Injector<ActorScheduler> getActorSchedulerInjector()
    {
        return actorSchedulerInjector;
    }

    public Injector<RaftContext> getRaftContextInjector()
    {
        return raftContextInjector;
    }
}

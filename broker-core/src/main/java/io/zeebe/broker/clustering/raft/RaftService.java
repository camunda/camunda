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
package io.zeebe.broker.clustering.raft;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import io.zeebe.logstreams.log.LogStream;
import io.zeebe.raft.Raft;
import io.zeebe.raft.RaftPersistentStorage;
import io.zeebe.raft.RaftStateListener;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import io.zeebe.transport.BufferingServerTransport;
import io.zeebe.transport.ClientTransport;
import io.zeebe.transport.SocketAddress;
import io.zeebe.util.actor.ActorReference;
import io.zeebe.util.actor.ActorScheduler;

public class RaftService implements Service<Raft>
{

    private final SocketAddress socketAddress;
    private final LogStream logStream;
    private final List<SocketAddress> members;
    private final RaftPersistentStorage persistentStorage;
    private final RaftStateListener raftStateListener;
    private Injector<ActorScheduler> actorSchedulerInjector = new Injector<>();
    private Injector<BufferingServerTransport> serverTransportInjector = new Injector<>();
    private Injector<ClientTransport> clientTransportInjector = new Injector<>();

    private Raft raft;
    private ActorReference actorReference;

    public RaftService(final SocketAddress socketAddress, final LogStream logStream, final List<SocketAddress> members, final RaftPersistentStorage persistentStorage, RaftStateListener raftStateListener)
    {
        this.socketAddress = socketAddress;
        this.logStream = logStream;
        this.members = members;
        this.persistentStorage = persistentStorage;
        this.raftStateListener = raftStateListener;
    }

    @Override
    public void start(final ServiceStartContext startContext)
    {

        final CompletableFuture<Void> startFuture =
            logStream.openAsync().thenAccept(v ->
            {
                final BufferingServerTransport serverTransport = serverTransportInjector.getValue();
                final ClientTransport clientTransport = clientTransportInjector.getValue();
                raft = new Raft(socketAddress, logStream, serverTransport, clientTransport, persistentStorage);
                raft.registerRaftStateListener(raftStateListener);

                raft.addMembers(members);

                final ActorScheduler actorScheduler = actorSchedulerInjector.getValue();
                actorReference = actorScheduler.schedule(raft);
            });

        startContext.async(startFuture);
    }

    @Override
    public void stop(final ServiceStopContext stopContext)
    {
        actorReference.close();
        raft.close();

        final CompletableFuture<Void> stopFuture =
            logStream.closeLogStreamController().thenCompose(v -> logStream.closeAsync());

        stopContext.async(stopFuture);
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

    public Injector<BufferingServerTransport> getServerTransportInjector()
    {
        return serverTransportInjector;
    }

    public Injector<ClientTransport> getClientTransportInjector()
    {
        return clientTransportInjector;
    }

}

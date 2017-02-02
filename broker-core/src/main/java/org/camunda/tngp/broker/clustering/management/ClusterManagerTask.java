package org.camunda.tngp.broker.clustering.management;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import org.camunda.tngp.broker.clustering.gossip.channel.ClientChannelManager;
import org.camunda.tngp.broker.clustering.gossip.data.Peer;
import org.camunda.tngp.broker.clustering.gossip.util.Requestor;
import org.camunda.tngp.broker.clustering.management.message.InvitationRequest;
import org.camunda.tngp.broker.clustering.raft.RaftProtocol;
import org.camunda.tngp.broker.clustering.raft.protocol.Raft;
import org.camunda.tngp.broker.clustering.worker.ManagementWorkerContext;
import org.camunda.tngp.transport.requestresponse.client.TransportConnection;
import org.camunda.tngp.transport.requestresponse.server.WorkerTask;

public class ClusterManagerTask implements WorkerTask<ManagementWorkerContext>
{
    protected InvitationRequest invitation = new InvitationRequest();
    protected final List<Requestor> activeRequestors = new CopyOnWriteArrayList<>();
    protected final Consumer<Runnable> logCmdConsumer = (c) -> c.run();

    @Override
    public int execute(final ManagementWorkerContext context)
    {
        final ClusterManager clusterManager = context.getClusterManager();
        final ClientChannelManager clientChannelManager = clusterManager.getClientChannelManager();
        final TransportConnection connection = clusterManager.getConnection();

        final RaftProtocol raftProtocol = clusterManager.getRaftProtocol();
        final List<Raft> rafts = raftProtocol.getRafts();

        int workcount = 0;

        // TODO: IMPROVE THIS!!!
        clusterManager.logCmdQueue.drain(logCmdConsumer);

        workcount += clusterManager.peerCmdQueue.drain((s) ->
        {

            if (rafts != null)
            {
                final Peer peer = s.get();

                for (int i = 0; i < rafts.size(); i++)
                {
                    final Raft raft = rafts.get(i);
                    if (raft != null && raft.isLeader() && raft.isUnderstaft())
                    {
                        System.out.println("sending invitiation " + raft.id());

                        final InvitationRequest invitationRequest = new InvitationRequest();
                        invitationRequest
                            .log(raft.id())
                            .cluster(new ArrayList<>(raft.members()));

                        final Requestor requestor = new Requestor(clientChannelManager, connection);
                        requestor.begin(peer.endpoint(), invitationRequest);
                        activeRequestors.add(requestor);
                    }
                }
            }
        });

        int i = 0;
        while (i < activeRequestors.size())
        {
            final Requestor requestor = activeRequestors.get(i);
            workcount += requestor.execute();

            if (requestor.isFailed() || requestor.isResponseAvailable())
            {
                activeRequestors.remove(i);
            }
            else
            {
                i++;
            }
        }

        return workcount;
    }

}

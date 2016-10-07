package org.camunda.tngp.broker.clustering.management.handler;

import java.util.List;

import org.agrona.DirectBuffer;
import org.camunda.tngp.broker.clustering.gossip.data.Peer;
import org.camunda.tngp.broker.clustering.management.message.InvitationRequest;
import org.camunda.tngp.broker.clustering.management.message.InvitationResponse;
import org.camunda.tngp.broker.clustering.raft.protocol.Member;
import org.camunda.tngp.broker.clustering.raft.protocol.Raft;
import org.camunda.tngp.broker.clustering.util.Endpoint;
import org.camunda.tngp.broker.clustering.worker.ManagementWorkerContext;
import org.camunda.tngp.broker.clustering.worker.spi.ManagementRequestHandler;
import org.camunda.tngp.logstreams.LogStreams;
import org.camunda.tngp.logstreams.log.LogStream;
import org.camunda.tngp.management.cluster.InvitationRequestEncoder;
import org.camunda.tngp.transport.requestresponse.server.DeferredResponse;
import org.camunda.tngp.util.agent.DedicatedAgentRunnerService;
import org.camunda.tngp.util.agent.SimpleAgentRunnerFactory;

public class InvitationRequestHandler implements ManagementRequestHandler
{
    protected InvitationRequest invitationRequest = new InvitationRequest();
    protected InvitationResponse invitationResponse = new InvitationResponse();

    protected final ManagementWorkerContext context;

    public InvitationRequestHandler(final ManagementWorkerContext context)
    {
        this.context = context;
    }

    @Override
    public long onRequest(final DirectBuffer msg, final int offset, final int length, final DeferredResponse response)
    {

        int workcount = 0;

        invitationRequest.wrap(msg, offset, length);

        final int raftId = invitationRequest.log();

        System.out.println("YEAH received invitation " + raftId);

        final List<Member> cluster = invitationRequest.cluster();

        final Peer localPeer = context.getGossipProtocol().getLocalPeer();
        final int port = localPeer.endpoint().port();
        final String host = localPeer.endpoint().host();

        final LogStream stream = LogStreams.createFsLogStream("raft-log-" + port, raftId)
                .deleteOnClose(false)
                .logDirectory("/tmp/raft-log-" + port + "-" + raftId)
                .agentRunnerService(new DedicatedAgentRunnerService(new SimpleAgentRunnerFactory()))
                .logSegmentSize(512 * 1024 * 1024)
                .build();
        stream.open();

        final Raft raft = Raft.builder(new Endpoint().port(port).host(host))
            .withStream(stream)
            .withClientChannelManager(context.getClusterManager().getClientChannelManager())
            .withConnection(context.getClusterManager().getConnection())
            .withDataFramePool(context.getClusterManager().getDataFramePool())
            .build()
            .join(cluster);

        context.getRaftProtocol().getRafts().add(raft);

        invitationResponse.acknowledged(true);
        if (response.allocateAndWrite(invitationResponse))
        {
            workcount += 1;
            response.commit();
        }
        else
        {
            response.abort();
        }


//      final int port = raftProtocol.getLocalEndpoint().port();
//      stream = LogStreams.createFsLogStream("raft-log-" + port, port)
//              .deleteOnClose(false)
//              .logDirectory("/tmp/raft-log-" + port)
//              .agentRunnerService(new DedicatedAgentRunnerService(new SimpleAgentRunnerFactory()))
//              .logSegmentSize(32 * 1024)
//              .build();
//      stream.open();

        return workcount;
    }

    @Override
    public int getTemplateId()
    {
        return InvitationRequestEncoder.TEMPLATE_ID;
    }

}

package org.camunda.tngp.broker.clustering.gossip.handler;

import org.agrona.DirectBuffer;
import org.camunda.tngp.broker.clustering.gossip.GossipProtocol;
import org.camunda.tngp.broker.clustering.gossip.data.PeerList;
import org.camunda.tngp.broker.clustering.gossip.message.GossipReader;
import org.camunda.tngp.broker.clustering.gossip.message.GossipWriter;
import org.camunda.tngp.broker.clustering.worker.ManagementWorkerContext;
import org.camunda.tngp.broker.clustering.worker.spi.ManagementRequestHandler;
import org.camunda.tngp.management.gossip.GossipDecoder;
import org.camunda.tngp.transport.requestresponse.server.DeferredResponse;

public class GossipRequestHandler implements ManagementRequestHandler
{
    protected final ManagementWorkerContext context;

    protected final PeerList diff = new PeerList(1000);

    protected final GossipReader reader = new GossipReader();
    protected final GossipWriter writer = new GossipWriter(diff);

    public GossipRequestHandler(final ManagementWorkerContext context)
    {
        this.context = context;
    }

    @Override
    public long onRequest(final DirectBuffer msg, final int offset, final int length, final DeferredResponse response)
    {
        int workcount = 0;

        diff.clear();

        final GossipProtocol gossipProtocol = context.getGossipProtocol();
        final PeerList members = gossipProtocol.getMembers();

        reader.wrap(msg, offset, length);
        members.merge(reader, diff);

        if (response.allocateAndWrite(writer))
        {
            workcount += 1;
            response.commit();
        }
        else
        {
            response.abort();
        }

        return workcount;
    }

    @Override
    public int getTemplateId()
    {
        return GossipDecoder.TEMPLATE_ID;
    }

}

package org.camunda.tngp.broker.clustering.gossip.handler;

import org.agrona.DirectBuffer;
import org.camunda.tngp.broker.clustering.gossip.GossipProtocol;
import org.camunda.tngp.broker.clustering.gossip.message.ProbeReader;
import org.camunda.tngp.broker.clustering.gossip.protocol.Probe;
import org.camunda.tngp.broker.clustering.worker.ManagementWorkerContext;
import org.camunda.tngp.broker.clustering.worker.spi.ManagementRequestHandler;
import org.camunda.tngp.management.gossip.ProbeDecoder;
import org.camunda.tngp.transport.requestresponse.server.DeferredResponse;

public class ProbeRequestHandler implements ManagementRequestHandler
{
    protected ManagementWorkerContext context;

    protected final ProbeReader reader = new ProbeReader();

    public ProbeRequestHandler(final ManagementWorkerContext context)
    {
        this.context = context;
    }

    @Override
    public long onRequest(final DirectBuffer msg, final int offset, final int length, final DeferredResponse response)
    {
        int workcount = 0;
        reader.wrap(msg, offset, length);

        final GossipProtocol gossipProtocol = context.getGossipProtocol();
        final Probe[] probers = gossipProtocol.getProbers();

        for (int i = 0; i < probers.length; i++)
        {
            if (probers[i].isClosed())
            {
                workcount += 1;
                probers[i].begin(reader.member(), response);
                break;
            }
        }

        return workcount;
    }

    @Override
    public int getTemplateId()
    {
        return ProbeDecoder.TEMPLATE_ID;
    }

}

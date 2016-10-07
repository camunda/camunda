package org.camunda.tngp.broker.clustering.raft.handler;

import java.util.List;

import org.agrona.DirectBuffer;
import org.camunda.tngp.broker.clustering.raft.message.VoteRequest;
import org.camunda.tngp.broker.clustering.raft.message.VoteResponse;
import org.camunda.tngp.broker.clustering.raft.protocol.Raft;
import org.camunda.tngp.broker.clustering.worker.ManagementWorkerContext;
import org.camunda.tngp.broker.clustering.worker.spi.ManagementRequestHandler;
import org.camunda.tngp.clustering.raft.VoteRequestDecoder;
import org.camunda.tngp.transport.requestresponse.server.DeferredResponse;

public class VoteRequestHandler implements ManagementRequestHandler
{
    protected final ManagementWorkerContext context;
    protected final VoteRequest voteRequest = new VoteRequest();
    protected final VoteResponse voteResponse = new VoteResponse();

    public VoteRequestHandler(final ManagementWorkerContext context)
    {
        this.context = context;
    }

    @Override
    public long onRequest(final DirectBuffer msg, final int offset, final int length, final DeferredResponse response)
    {
        voteRequest.reset();
        voteRequest.wrap(msg, offset, length);

        Raft raft = null;

        final List<Raft> rafts = context.getRaftProtocol().getRafts();
        for (int i = 0; i < rafts.size(); i++)
        {
            if (rafts.get(i).stream().getId() == voteRequest.log())
            {
                raft = rafts.get(i);
                break;
            }
        }

        if (raft != null)
        {
            raft.handleVote(voteRequest, voteResponse);

            if (response.allocateAndWrite(voteResponse))
            {
                response.commit();
            }
            else
            {
                response.abort();
            }
        }

        return 1;
    }

    @Override
    public int getTemplateId()
    {
        return VoteRequestDecoder.TEMPLATE_ID;
    }

}

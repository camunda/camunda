package org.camunda.tngp.broker.clustering.raft.handler;

import java.util.List;

import org.agrona.DirectBuffer;
import org.camunda.tngp.broker.clustering.raft.message.AppendResponse;
import org.camunda.tngp.broker.clustering.raft.protocol.Raft;
import org.camunda.tngp.broker.clustering.worker.ManagementWorkerContext;
import org.camunda.tngp.broker.clustering.worker.spi.ManagementDataFrameHandler;
import org.camunda.tngp.clustering.raft.AppendResponseEncoder;

public class AppendResponseHandler implements ManagementDataFrameHandler
{
    protected final ManagementWorkerContext context;
    protected final AppendResponse appendEntriesResponse = new AppendResponse();

    public AppendResponseHandler(final ManagementWorkerContext context)
    {
        this.context = context;
    }

    @Override
    public int onDataFrame(DirectBuffer msg, int offset, int length, int channelId)
    {
        appendEntriesResponse.reset();
        appendEntriesResponse.wrap(msg, offset, length);

        int workcount = 0;

        Raft raft = null;

        final List<Raft> rafts = context.getRaftProtocol().getRafts();
        for (int i = 0; i < rafts.size(); i++)
        {
            if (rafts.get(i).stream().getId() == appendEntriesResponse.log())
            {
                raft = rafts.get(i);
                break;
            }
        }

        if (raft != null)
        {

            raft.handleAppendResponse(appendEntriesResponse);
            workcount += 1;
        }

        return workcount;
    }

    @Override
    public int getTemplateId()
    {
        return AppendResponseEncoder.TEMPLATE_ID;
    }

}

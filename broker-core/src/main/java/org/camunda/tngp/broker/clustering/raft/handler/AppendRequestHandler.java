package org.camunda.tngp.broker.clustering.raft.handler;

import java.util.List;

import org.agrona.DirectBuffer;
import org.camunda.tngp.broker.clustering.raft.message.AppendRequest;
import org.camunda.tngp.broker.clustering.raft.message.AppendResponse;
import org.camunda.tngp.broker.clustering.raft.protocol.Raft;
import org.camunda.tngp.broker.clustering.worker.ManagementWorkerContext;
import org.camunda.tngp.broker.clustering.worker.spi.ManagementDataFrameHandler;
import org.camunda.tngp.clustering.raft.AppendRequestDecoder;
import org.camunda.tngp.transport.singlemessage.OutgoingDataFrame;

public class AppendRequestHandler implements ManagementDataFrameHandler
{
    protected final ManagementWorkerContext context;
    protected final AppendRequest appendEntriesRequest = new AppendRequest();

    public AppendRequestHandler(final ManagementWorkerContext context)
    {
        this.context = context;
    }

    @Override
    public int onDataFrame(final DirectBuffer msg, final int offset, final int length, int channelId)
    {
        appendEntriesRequest.reset();
        appendEntriesRequest.wrap(msg, offset, length);

        Raft raft = null;

        final List<Raft> rafts = context.getRaftProtocol().getRafts();
        for (int i = 0; i < rafts.size(); i++)
        {
            if (rafts.get(i).stream().getId() == appendEntriesRequest.log())
            {
                raft = rafts.get(i);
                break;
            }
        }

        if (raft != null)
        {
//            System.out.println("append request received: now: " + System.currentTimeMillis() + ", " + appendEntriesRequest.index());
            final AppendResponse response = raft.handleAppendRequest(appendEntriesRequest);

            if (response != null)
            {
                final int responseLength = response.getLength();
                final OutgoingDataFrame frame = raft.context().dataFramePool().openFrame(channelId, responseLength);

                if (frame != null)
                {
                    try
                    {
                        frame.write(response);
                        frame.commit();
                    }
                    catch (final Exception e)
                    {
                        e.printStackTrace();
                        frame.abort();
                    }
                }
            }
        }

        return 1;
    }

    @Override
    public int getTemplateId()
    {
        return AppendRequestDecoder.TEMPLATE_ID;
    }

}

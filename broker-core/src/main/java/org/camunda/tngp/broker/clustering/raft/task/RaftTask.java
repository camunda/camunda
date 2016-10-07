package org.camunda.tngp.broker.clustering.raft.task;

import java.util.List;

import org.camunda.tngp.broker.clustering.raft.RaftProtocol;
import org.camunda.tngp.broker.clustering.raft.protocol.Raft;
import org.camunda.tngp.broker.clustering.worker.ManagementWorkerContext;
import org.camunda.tngp.transport.requestresponse.server.WorkerTask;

public class RaftTask implements WorkerTask<ManagementWorkerContext>
{

    @Override
    public int execute(final ManagementWorkerContext context)
    {
        int workcount = 0;

        final RaftProtocol raftProtocol = context.getRaftProtocol();

        workcount += executeRaft(raftProtocol);

        return workcount;
    }

    protected int executeRaft(final RaftProtocol raftProtocol)
    {
        int workcount = 0;

        final List<Raft> rafts = raftProtocol.getRafts();

        for (int i = 0; i < rafts.size(); i++)
        {
            workcount += rafts.get(i).doWork();
        }
        return workcount;
    }
}

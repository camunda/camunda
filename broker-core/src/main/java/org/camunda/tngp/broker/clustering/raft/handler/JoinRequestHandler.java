package org.camunda.tngp.broker.clustering.raft.handler;

import java.util.List;

import org.agrona.DirectBuffer;
import org.camunda.tngp.broker.clustering.raft.RaftProtocol;
import org.camunda.tngp.broker.clustering.raft.message.JoinRequest;
import org.camunda.tngp.broker.clustering.raft.message.JoinResponse;
import org.camunda.tngp.broker.clustering.raft.protocol.Raft;
import org.camunda.tngp.broker.clustering.worker.ManagementWorkerContext;
import org.camunda.tngp.broker.clustering.worker.spi.ManagementRequestHandler;
import org.camunda.tngp.clustering.raft.JoinRequestEncoder;
import org.camunda.tngp.transport.requestresponse.server.DeferredResponse;

public class JoinRequestHandler implements ManagementRequestHandler
{
    protected final JoinRequest joinRequest = new JoinRequest();
    protected final JoinResponse joinResponse = new JoinResponse();

    protected final ManagementWorkerContext context;

    public JoinRequestHandler(final ManagementWorkerContext context)
    {
        this.context = context;
    }

    @Override
    public long onRequest(final DirectBuffer msg, final int offset, final int length, final DeferredResponse response)
    {
        joinRequest.reset();
        joinRequest.wrap(msg, offset, length);

        System.out.println("YEAH join request received " + joinRequest.log());

        final RaftProtocol raftProtocol = context.getRaftProtocol();
        final List<Raft> rafts = raftProtocol.getRafts();
        Raft raft = null;

        final int log = joinRequest.log();
        for (int i = 0; i < rafts.size(); i++)
        {
            if (rafts.get(i).id() == log)
            {
                raft = rafts.get(i);
                break;
            }
        }

//        final JoinResponse joinResponse = raft.handleJoin(joinRequest);
//
//        if (joinResponse != null)
//        {
//            if (response.allocateAndWrite(joinResponse))
//            {
//                response.commit();
//            }
//            else
//            {
//                response.abort();
//            }
//        }

        return 1;
    }

    @Override
    public int getTemplateId()
    {
        return JoinRequestEncoder.TEMPLATE_ID;
    }

}

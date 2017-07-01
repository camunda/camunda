package io.zeebe.client.clustering.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.zeebe.client.clustering.RequestTopologyCmd;
import io.zeebe.client.clustering.Topology;
import io.zeebe.client.impl.ClientCommandManager;
import io.zeebe.client.impl.cmd.AbstractControlMessageCmd;
import io.zeebe.protocol.clientapi.ControlMessageType;

public class RequestTopologyCmdImpl extends AbstractControlMessageCmd<TopologyImpl, Topology> implements RequestTopologyCmd
{

    public RequestTopologyCmdImpl(final ClientCommandManager commandManager, final ObjectMapper objectMapper)
    {
        super(commandManager, objectMapper, null, TopologyImpl.class, ControlMessageType.REQUEST_TOPOLOGY);
    }

    @Override
    protected Object writeCommand()
    {
        return null;
    }

    @Override
    public void validate()
    {
    }

    @Override
    protected void reset()
    {
    }

    @Override
    protected TopologyImpl getResponseValue(final TopologyImpl topology)
    {
        return topology;
    }
}

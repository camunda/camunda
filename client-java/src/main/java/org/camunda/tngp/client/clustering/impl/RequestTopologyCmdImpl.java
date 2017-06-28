package org.camunda.tngp.client.clustering.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.tngp.client.clustering.RequestTopologyCmd;
import org.camunda.tngp.client.clustering.Topology;
import org.camunda.tngp.client.impl.ClientCommandManager;
import org.camunda.tngp.client.impl.cmd.AbstractControlMessageCmd;
import org.camunda.tngp.protocol.clientapi.ControlMessageType;

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

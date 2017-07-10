/**
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

/**
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.workflow.graph.model;

import org.agrona.DirectBuffer;

import io.zeebe.util.buffer.BufferUtil;

public class ExecutableScope extends ExecutableFlowNode
{
    private ExecutableFlowElement[] flowElements;

    private ExecutableStartEvent scopeStartEvent;

    public ExecutableFlowElement[] getFlowElements()
    {
        return flowElements;
    }

    public void setFlowElements(ExecutableFlowElement[] flowElements)
    {
        this.flowElements = flowElements;
    }

    public <T extends ExecutableFlowElement> T getChildById(String id)
    {
        return getChildById(BufferUtil.wrapString(id));
    }

    @SuppressWarnings("unchecked")
    public <T extends ExecutableFlowElement> T getChildById(DirectBuffer id)
    {
        for (int i = 0; i < flowElements.length; i++)
        {
            final ExecutableFlowElement flowElement = flowElements[i];

            if (BufferUtil.equals(id, flowElement.getId()))
            {
                return (T) flowElement;
            }
        }

        return null;
    }

    public ExecutableStartEvent getScopeStartEvent()
    {
        return scopeStartEvent;
    }

    public void setScopeStartEvent(ExecutableStartEvent scopeStartEvent)
    {
        this.scopeStartEvent = scopeStartEvent;
    }
}

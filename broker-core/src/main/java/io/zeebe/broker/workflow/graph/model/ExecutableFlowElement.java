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

public class ExecutableFlowElement
{
    private DirectBuffer id;

    private String name;
    private ExecutableScope flowScope;
    private ExecutableWorkflow workflow;

    public void setId(String id)
    {
        this.id = BufferUtil.wrapString(id);
    }

    public DirectBuffer getId()
    {
        return id;
    }

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public ExecutableScope getFlowScope()
    {
        return flowScope;
    }

    public void setFlowScope(ExecutableScope flowScope)
    {
        this.flowScope = flowScope;
    }

    public ExecutableWorkflow getWorkflow()
    {
        return workflow;
    }

    public void setWorkflow(ExecutableWorkflow process)
    {
        this.workflow = process;
    }

}

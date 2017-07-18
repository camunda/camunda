/*
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

import java.util.HashMap;
import java.util.Map;

import org.agrona.DirectBuffer;

public class ExecutableWorkflow extends ExecutableScope
{
    private final Map<DirectBuffer, ExecutableFlowElement> flowElementMap = new HashMap<>();

    private int version;

    public ExecutableWorkflow()
    {
        setWorkflow(this);
        setFlowScope(this);
    }

    public ExecutableFlowElement findFlowElementById(DirectBuffer id)
    {
        return flowElementMap.get(id);
    }

    public Map<DirectBuffer, ExecutableFlowElement> getFlowElementMap()
    {
        return flowElementMap;
    }

    public void setVersion(int version)
    {
        this.version = version;
    }

    public int getVersion()
    {
        return version;
    }

}

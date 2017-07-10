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

public class ExecutableSequenceFlow extends ExecutableFlowElement
{
    private ExecutableFlowNode sourceNode;
    private ExecutableFlowNode targetNode;

    public ExecutableFlowNode getSourceNode()
    {
        return sourceNode;
    }

    public ExecutableFlowNode getTargetNode()
    {
        return targetNode;
    }

    public void setSourceNode(ExecutableFlowNode sourceNode)
    {
        this.sourceNode = sourceNode;
    }

    public void setTargetNode(ExecutableFlowNode targetNode)
    {
        this.targetNode = targetNode;
    }
}

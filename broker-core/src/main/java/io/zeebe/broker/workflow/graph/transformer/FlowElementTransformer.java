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
package io.zeebe.broker.workflow.graph.transformer;

import org.camunda.bpm.model.bpmn.instance.FlowElement;

import io.zeebe.broker.workflow.graph.model.ExecutableFlowElement;
import io.zeebe.broker.workflow.graph.model.ExecutableScope;

public class FlowElementTransformer implements BpmnElementTransformer<FlowElement, ExecutableFlowElement>
{
    @Override
    public Class<FlowElement> getType()
    {
        return FlowElement.class;
    }

    @Override
    public void transform(FlowElement modelElement, ExecutableFlowElement bpmnElement, ExecutableScope scope)
    {
        bpmnElement.setName(modelElement.getName());
    }

}

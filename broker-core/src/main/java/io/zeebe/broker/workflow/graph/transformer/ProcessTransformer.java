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

import static io.zeebe.util.EnsureUtil.ensureNotNull;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.camunda.bpm.model.bpmn.instance.FlowElement;
import org.camunda.bpm.model.bpmn.instance.Process;

import io.zeebe.broker.workflow.graph.model.BpmnFactory;
import io.zeebe.broker.workflow.graph.model.ExecutableFlowElement;
import io.zeebe.broker.workflow.graph.model.ExecutableScope;
import io.zeebe.broker.workflow.graph.model.ExecutableStartEvent;
import io.zeebe.broker.workflow.graph.model.ExecutableWorkflow;

public class ProcessTransformer implements BpmnElementTransformer<Process, ExecutableWorkflow>
{
    @Override
    public Class<Process> getType()
    {
        return Process.class;
    }

    @Override
    public void transform(Process modelElement, ExecutableWorkflow executableWorkflow, ExecutableScope scope)
    {
        executableWorkflow.setId(modelElement.getId());
        executableWorkflow.setName(modelElement.getName());

        final Collection<FlowElement> flowElements = modelElement.getChildElementsByType(FlowElement.class);
        transformChildElements(executableWorkflow, scope, flowElements);

        setStartEvent(executableWorkflow);
    }

    private void transformChildElements(ExecutableWorkflow executableWorkflow, ExecutableScope scope, final Collection<FlowElement> flowElements)
    {
        final Map<FlowElement, ExecutableFlowElement> executableFlowElements = new HashMap<>();

        for (FlowElement flowElement : flowElements)
        {
            final ExecutableFlowElement executableElement = BpmnFactory.createElement(flowElement);

            executableElement.setId(flowElement.getId());
            executableElement.setFlowScope(scope);
            executableElement.setWorkflow(executableWorkflow);

            executableWorkflow.getFlowElementMap().put(executableElement.getId(), executableElement);

            executableFlowElements.put(flowElement, executableElement);
        }

        scope.setFlowElements(executableFlowElements.values().toArray(new ExecutableFlowElement[executableFlowElements.size()]));

        for (Entry<FlowElement, ExecutableFlowElement> e : executableFlowElements.entrySet())
        {
            Transformers.apply(e.getKey(), e.getValue(), executableWorkflow);
        }
    }

    private void setStartEvent(ExecutableWorkflow executableWorkflow)
    {
        for (ExecutableFlowElement flowElement : executableWorkflow.getFlowElements())
        {
            if (flowElement instanceof ExecutableStartEvent)
            {
                final ExecutableStartEvent startEvent = (ExecutableStartEvent) flowElement;

                if (executableWorkflow.getScopeStartEvent() == null)
                {
                    executableWorkflow.setScopeStartEvent(startEvent);
                }
                else
                {
                    throw new RuntimeException("a workflow can only have one start event");
                }
            }
        }
        ensureNotNull("start event", executableWorkflow.getScopeStartEvent());
    }

}

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

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.agrona.LangUtil;
import org.camunda.bpm.model.bpmn.instance.BaseElement;
import org.camunda.bpm.model.bpmn.instance.EndEvent;
import org.camunda.bpm.model.bpmn.instance.Process;
import org.camunda.bpm.model.bpmn.instance.SequenceFlow;
import org.camunda.bpm.model.bpmn.instance.ServiceTask;
import org.camunda.bpm.model.bpmn.instance.StartEvent;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;

public class BpmnFactory
{
    private static final Map<Class< ? extends BaseElement>, Class<? extends ExecutableFlowElement>> TYPE_MAPPING = new HashMap<>();

    static
    {
        TYPE_MAPPING.put(StartEvent.class, ExecutableStartEvent.class);
        TYPE_MAPPING.put(EndEvent.class, ExecutableEndEvent.class);
        TYPE_MAPPING.put(ServiceTask.class, ExecutableServiceTask.class);
        TYPE_MAPPING.put(SequenceFlow.class, ExecutableSequenceFlow.class);
        TYPE_MAPPING.put(Process.class, ExecutableWorkflow.class);
    }

    public static ExecutableFlowElement createElement(BaseElement element)
    {
        final Class<? extends ModelElementInstance> modelElementType = element.getElementType().getInstanceType();
        final Class<? extends ExecutableFlowElement> type = TYPE_MAPPING.get(modelElementType);

        if (type != null)
        {
            try
            {
                return type.newInstance();
            }
            catch (InstantiationException | IllegalAccessException e)
            {
                LangUtil.rethrowUnchecked(e);
            }
        }

        throw new RuntimeException("No type mapping for type " + modelElementType);
    }

    public static Collection<Class<? extends BaseElement>> getSupportedTypes()
    {
        return TYPE_MAPPING.keySet();
    }

}

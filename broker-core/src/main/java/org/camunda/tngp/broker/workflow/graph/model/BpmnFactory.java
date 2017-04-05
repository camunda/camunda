package org.camunda.tngp.broker.workflow.graph.model;

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

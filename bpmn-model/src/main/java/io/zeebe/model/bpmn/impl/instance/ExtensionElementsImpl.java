package io.zeebe.model.bpmn.impl.instance;

import javax.xml.bind.annotation.XmlElement;

import io.zeebe.model.bpmn.BpmnConstants;
import io.zeebe.model.bpmn.impl.metadata.TaskDefinitionImpl;
import io.zeebe.model.bpmn.impl.metadata.TaskHeadersImpl;

public class ExtensionElementsImpl
{
    private TaskDefinitionImpl taskDefinition;
    private TaskHeadersImpl taskHeaders;

    @XmlElement(name = BpmnConstants.ZEEBE_ELEMENT_TASK_DEFINITION, namespace = BpmnConstants.ZEEBE_NS)
    public void setTaskDefinition(TaskDefinitionImpl taskDefinition)
    {
        this.taskDefinition = taskDefinition;
    }

    public TaskDefinitionImpl getTaskDefinition()
    {
        return taskDefinition;
    }

    @XmlElement(name = BpmnConstants.ZEEBE_ELEMENT_TASK_HEADERS, namespace = BpmnConstants.ZEEBE_NS)
    public void setTaskHeaders(TaskHeadersImpl taskHeaders)
    {
        this.taskHeaders = taskHeaders;
    }

    public TaskHeadersImpl getTaskHeaders()
    {
        return taskHeaders;
    }

}

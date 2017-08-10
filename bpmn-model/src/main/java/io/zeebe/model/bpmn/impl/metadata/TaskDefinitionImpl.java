package io.zeebe.model.bpmn.impl.metadata;

import static io.zeebe.util.buffer.BufferUtil.bufferAsString;
import static io.zeebe.util.buffer.BufferUtil.wrapString;

import javax.xml.bind.annotation.XmlAttribute;

import io.zeebe.model.bpmn.BpmnConstants;
import io.zeebe.model.bpmn.instance.TaskDefinition;
import org.agrona.DirectBuffer;

public class TaskDefinitionImpl implements TaskDefinition
{
    private DirectBuffer type;
    private int retries = DEFAULT_TASK_RETRIES;

    @XmlAttribute(name = BpmnConstants.ZEEBE_ATTRIBUTE_TASK_TYPE)
    public void setType(String type)
    {
        this.type = wrapString(type);
    }

    public String getType()
    {
        return type != null ? bufferAsString(type) : null;
    }

    @XmlAttribute(name = BpmnConstants.ZEEBE_ATTRIBUTES_TASK_RETRIES)
    public void setRetries(int reties)
    {
        this.retries = reties;
    }

    @Override
    public int getRetries()
    {
        return retries;
    }

    @Override
    public DirectBuffer getTypeAsBuffer()
    {
        return type;
    }

    @Override
    public String toString()
    {
        final StringBuilder builder = new StringBuilder();
        builder.append("TaskDefinition [type=");
        builder.append(getType());
        builder.append(", retries=");
        builder.append(retries);
        builder.append("]");
        return builder.toString();
    }

}

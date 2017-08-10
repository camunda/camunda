package io.zeebe.model.bpmn.impl.metadata;

import javax.xml.bind.annotation.XmlAttribute;

import io.zeebe.model.bpmn.BpmnConstants;

public class TaskHeaderImpl
{
    private String key;
    private String value;
    
    @XmlAttribute(name = BpmnConstants.ZEEBE_ATTRIBUTE_TASK_HEADER_KEY)
    public void setKey(String key)
    {
        this.key = key;
    }

    public String getKey()
    {
        return key;
    }
    
    @XmlAttribute(name = BpmnConstants.ZEEBE_ATTRIBUTE_TASK_HEADER_VALUE)
    public void setValue(String value)
    {
        this.value = value;
    }

    public String getValue()
    {
        return value;
    }

    @Override
    public String toString()
    {
        StringBuilder builder = new StringBuilder();
        builder.append("TaskHeader [key=");
        builder.append(key);
        builder.append(", value=");
        builder.append(value);
        builder.append("]");
        return builder.toString();
    }
    
}

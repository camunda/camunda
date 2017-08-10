package io.zeebe.model.bpmn.impl.metadata;

import javax.xml.bind.annotation.XmlAttribute;

import io.zeebe.model.bpmn.BpmnConstants;
import io.zeebe.model.bpmn.instance.InputOutputMapping;

public class MappingImpl
{
    private String source = InputOutputMapping.DEFAULT_MAPPING;
    private String target = InputOutputMapping.DEFAULT_MAPPING;

    @XmlAttribute(name = BpmnConstants.ZEEBE_ATTRIBUTE_MAPPING_SOURCE)
    public void setSource(String source)
    {
        this.source = source;
    }

    public String getSource()
    {
        return source;
    }

    @XmlAttribute(name = BpmnConstants.ZEEBE_ATTRIBUTE_MAPPING_TARGET)
    public void setTarget(String target)
    {
        this.target = target;
    }

    public String getTarget()
    {
        return target;
    }

    @Override
    public String toString()
    {
        final StringBuilder builder = new StringBuilder();
        builder.append("Mapping [source=");
        builder.append(source);
        builder.append(", target=");
        builder.append(target);
        builder.append("]");
        return builder.toString();
    }

}

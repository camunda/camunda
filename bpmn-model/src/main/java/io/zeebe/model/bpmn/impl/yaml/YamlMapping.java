package io.zeebe.model.bpmn.impl.yaml;

import io.zeebe.model.bpmn.instance.InputOutputMapping;

public class YamlMapping
{
    private String source = InputOutputMapping.DEFAULT_MAPPING;
    private String target = InputOutputMapping.DEFAULT_MAPPING;

    public String getSource()
    {
        return source;
    }

    public void setSource(String source)
    {
        this.source = source;
    }

    public String getTarget()
    {
        return target;
    }

    public void setTarget(String target)
    {
        this.target = target;
    }

}

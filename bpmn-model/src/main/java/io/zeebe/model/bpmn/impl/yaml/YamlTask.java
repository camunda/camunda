package io.zeebe.model.bpmn.impl.yaml;

import java.util.*;

import io.zeebe.model.bpmn.instance.TaskDefinition;

public class YamlTask
{
    private String id = "";

    private String type = "";
    private int retries = TaskDefinition.DEFAULT_TASK_RETRIES;

    private Map<String, String> headers = new HashMap<>();

    private List<YamlMapping> inputs = new ArrayList<>();
    private List<YamlMapping> outputs = new ArrayList<>();

    public String getId()
    {
        return id;
    }

    public void setId(String id)
    {
        this.id = id;
    }

    public String getType()
    {
        return type;
    }

    public void setType(String type)
    {
        this.type = type;
    }

    public int getRetries()
    {
        return retries;
    }

    public void setRetries(int retries)
    {
        this.retries = retries;
    }

    public Map<String, String> getHeaders()
    {
        return headers;
    }

    public void setHeaders(Map<String, String> headers)
    {
        this.headers = headers;
    }

    public List<YamlMapping> getInputs()
    {
        return inputs;
    }

    public void setInputs(List<YamlMapping> inputs)
    {
        this.inputs = inputs;
    }

    public List<YamlMapping> getOutputs()
    {
        return outputs;
    }

    public void setOutputs(List<YamlMapping> outputs)
    {
        this.outputs = outputs;
    }

}

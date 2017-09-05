package io.zeebe.model.bpmn.impl.yaml;

import java.util.ArrayList;
import java.util.List;

public class YamlDefinitionImpl
{
    private String name = "";

    private List<YamlTask> tasks = new ArrayList<>();

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public List<YamlTask> getTasks()
    {
        return tasks;
    }

    public void setTasks(List<YamlTask> tasks)
    {
        this.tasks = tasks;
    }

}

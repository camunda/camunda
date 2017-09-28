/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.model.bpmn.impl.yaml;

import java.util.*;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.zeebe.model.bpmn.instance.TaskDefinition;

public class YamlTask
{
    private String id = "";

    private String type = "";
    private int retries = TaskDefinition.DEFAULT_TASK_RETRIES;

    private Map<String, String> headers = new HashMap<>();

    private List<YamlMapping> inputs = new ArrayList<>();
    private List<YamlMapping> outputs = new ArrayList<>();

    private boolean end = false;

    @JsonProperty("goto")
    private String next;

    @JsonProperty("switch")
    private List<YamlCase> cases = new ArrayList<>();

    @JsonProperty("default")
    private String defaultCase;

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


    public List<YamlCase> getCases()
    {
        return cases;
    }

    public void setCases(List<YamlCase> cases)
    {
        this.cases = cases;
    }

    public String getNext()
    {
        return next;
    }

    public void setNext(String next)
    {
        this.next = next;
    }

    public String getDefaultCase()
    {
        return defaultCase;
    }

    public void setDefaultCase(String defaultCase)
    {
        this.defaultCase = defaultCase;
    }

    public boolean isEnd()
    {
        return end;
    }

    public void setEnd(boolean end)
    {
        this.end = end;
    }

}

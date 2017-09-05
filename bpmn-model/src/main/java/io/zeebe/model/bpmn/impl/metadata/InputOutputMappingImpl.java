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
package io.zeebe.model.bpmn.impl.metadata;

import java.util.*;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlTransient;

import io.zeebe.model.bpmn.BpmnConstants;
import io.zeebe.model.bpmn.impl.instance.BaseElement;
import io.zeebe.model.bpmn.instance.InputOutputMapping;
import io.zeebe.msgpack.mapping.Mapping;

public class InputOutputMappingImpl extends BaseElement implements InputOutputMapping
{
    private List<MappingImpl> inputs  = new ArrayList<>();
    private List<MappingImpl> outputs = new ArrayList<>();

    private Mapping[] inputMappings;
    private Mapping[] ouputMappings;

    @XmlElement(name = BpmnConstants.ZEEBE_ELEMENT_MAPPING_INPUT, namespace = BpmnConstants.ZEEBE_NS)
    public void setInputs(List<MappingImpl> inputs)
    {
        this.inputs = inputs;
    }

    public List<MappingImpl> getInputs()
    {
        return inputs;
    }

    @XmlElement(name = BpmnConstants.ZEEBE_ELEMENT_MAPPING_OUTPUT, namespace = BpmnConstants.ZEEBE_NS)
    public void setOutputs(List<MappingImpl> outputs)
    {
        this.outputs = outputs;
    }

    public List<MappingImpl> getOutputs()
    {
        return outputs;
    }

    @XmlTransient
    public void setInputMappings(Mapping[] inputMappings)
    {
        this.inputMappings = inputMappings;
    }

    @Override
    public Mapping[] getInputMappings()
    {
        return inputMappings;
    }

    @XmlTransient
    public void setOutputMappings(Mapping[] outputMappings)
    {
        this.ouputMappings = outputMappings;
    }

    @Override
    public Mapping[] getOutputMappings()
    {
        return ouputMappings;
    }

    @Override
    public Map<String, String> getInputMappingsAsMap()
    {
        final Map<String, String> map = new HashMap<>();

        for (MappingImpl input : inputs)
        {
            map.put(input.getSource(), input.getTarget());
        }

        return map;
    }

    @Override
    public Map<String, String> getOutputMappingsAsMap()
    {
        final Map<String, String> map = new HashMap<>();

        for (MappingImpl output : outputs)
        {
            map.put(output.getSource(), output.getTarget());
        }

        return map;
    }

    @Override
    public String toString()
    {
        final StringBuilder builder = new StringBuilder();
        builder.append("InputOutputMapping [inputs=");
        builder.append(inputs);
        builder.append(", outputs=");
        builder.append(outputs);
        builder.append("]");
        return builder.toString();
    }

}

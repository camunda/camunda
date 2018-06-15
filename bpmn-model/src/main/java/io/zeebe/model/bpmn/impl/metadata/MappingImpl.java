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

import javax.xml.bind.annotation.XmlAttribute;

import io.zeebe.model.bpmn.BpmnConstants;
import io.zeebe.model.bpmn.impl.instance.BaseElement;
import io.zeebe.model.bpmn.instance.InputOutputMapping;

public class MappingImpl extends BaseElement
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

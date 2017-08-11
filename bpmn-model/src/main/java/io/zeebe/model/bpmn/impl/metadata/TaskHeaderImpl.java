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
        final StringBuilder builder = new StringBuilder();
        builder.append("TaskHeader [key=");
        builder.append(key);
        builder.append(", value=");
        builder.append(value);
        builder.append("]");
        return builder.toString();
    }

}

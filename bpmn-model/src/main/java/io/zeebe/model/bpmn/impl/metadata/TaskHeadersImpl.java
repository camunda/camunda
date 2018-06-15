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

import io.zeebe.model.bpmn.BpmnConstants;
import io.zeebe.model.bpmn.impl.instance.BaseElement;
import io.zeebe.model.bpmn.instance.TaskHeaders;
import org.agrona.DirectBuffer;

public class TaskHeadersImpl extends BaseElement implements TaskHeaders
{
    private List<TaskHeaderImpl> taskHeaders = new ArrayList<>();

    private DirectBuffer buffer;

    @XmlElement(name = BpmnConstants.ZEEBE_ELEMENT_TASK_HEADER, namespace = BpmnConstants.ZEEBE_NS)
    public void setTaskHeaders(List<TaskHeaderImpl> taskHeaders)
    {
        this.taskHeaders = taskHeaders;
    }

    public List<TaskHeaderImpl> getTaskHeaders()
    {
        return taskHeaders;
    }

    public void setEncodedMsgpack(DirectBuffer buffer)
    {
        this.buffer = buffer;
    }

    @Override
    public DirectBuffer asMsgpackEncoded()
    {
        return buffer;
    }

    @Override
    public Map<String, String> asMap()
    {
        final Map<String, String> map = new HashMap<>();
        for (TaskHeaderImpl header : taskHeaders)
        {
            map.put(header.getKey(), header.getValue());
        }

        return map;
    }

    @Override
    public boolean isEmpty()
    {
        return taskHeaders.isEmpty();
    }

    @Override
    public String toString()
    {
        final StringBuilder builder = new StringBuilder();
        builder.append("TaskHeaders ");
        builder.append(asMap());
        return builder.toString();
    }

}

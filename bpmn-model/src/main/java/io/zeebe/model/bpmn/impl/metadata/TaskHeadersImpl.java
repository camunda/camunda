package io.zeebe.model.bpmn.impl.metadata;

import java.util.*;

import javax.xml.bind.annotation.XmlElement;

import io.zeebe.model.bpmn.BpmnConstants;
import io.zeebe.model.bpmn.instance.TaskHeaders;
import org.agrona.DirectBuffer;

public class TaskHeadersImpl implements TaskHeaders
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
    public String toString()
    {
        final StringBuilder builder = new StringBuilder();
        builder.append("TaskHeaders ");
        builder.append(asMap());
        return builder.toString();
    }

}

package io.zeebe.model.bpmn.instance;

import java.util.Map;

import org.agrona.DirectBuffer;

public interface Workflow
{
    boolean isExecutable();

    DirectBuffer getBpmnProcessId();

    StartEvent getInitialStartEvent();

    FlowElement findFlowElementById(DirectBuffer id);

    Map<DirectBuffer, FlowElement> getFlowElementMap();

}

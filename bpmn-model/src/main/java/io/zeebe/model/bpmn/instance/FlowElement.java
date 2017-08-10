package io.zeebe.model.bpmn.instance;

import org.agrona.DirectBuffer;

public interface FlowElement
{

    DirectBuffer getIdAsBuffer();

}

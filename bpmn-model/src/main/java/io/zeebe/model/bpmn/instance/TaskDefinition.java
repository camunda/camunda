package io.zeebe.model.bpmn.instance;

import org.agrona.DirectBuffer;

public interface TaskDefinition
{
    int DEFAULT_TASK_RETRIES = 3;

    DirectBuffer getTypeAsBuffer();

    int getRetries();

}

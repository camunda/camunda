package org.camunda.tngp.log;

import uk.co.real_logic.agrona.DirectBuffer;

public interface LogEntryWriteListener
{
    void beforeCommit(DirectBuffer buffer, int offset, int length);

}

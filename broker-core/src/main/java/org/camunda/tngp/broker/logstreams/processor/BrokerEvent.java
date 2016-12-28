package org.camunda.tngp.broker.logstreams.processor;

import org.camunda.tngp.broker.taskqueue.processor.stuff.DataStuff;
import org.camunda.tngp.util.buffer.BufferWriter;

public interface BrokerEvent extends DataStuff, BufferWriter
{

}

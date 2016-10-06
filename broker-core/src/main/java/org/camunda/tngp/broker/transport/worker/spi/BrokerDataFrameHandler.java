package org.camunda.tngp.broker.transport.worker.spi;

import org.agrona.DirectBuffer;

public interface BrokerDataFrameHandler<C extends ResourceContext>
{

    int onDataFrame(C context, DirectBuffer message, int offset, int length);
}

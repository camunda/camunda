package org.camunda.tngp.broker.clustering.worker.spi;

import org.agrona.DirectBuffer;

public interface ManagementDataFrameHandler
{
    int onDataFrame(DirectBuffer message, int offset, int length, int channelId);

    int getTemplateId();
}

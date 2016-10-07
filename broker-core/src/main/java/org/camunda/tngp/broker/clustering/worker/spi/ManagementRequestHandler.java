package org.camunda.tngp.broker.clustering.worker.spi;

import org.agrona.DirectBuffer;
import org.camunda.tngp.transport.requestresponse.server.DeferredResponse;


public interface ManagementRequestHandler
{
    long onRequest(
            DirectBuffer msg,
            int offset,
            int length,
            DeferredResponse response);

    int getTemplateId();
}

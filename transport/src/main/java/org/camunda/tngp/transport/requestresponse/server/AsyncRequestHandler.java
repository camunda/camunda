package org.camunda.tngp.transport.requestresponse.server;

import org.agrona.DirectBuffer;

public interface AsyncRequestHandler
{

    long onRequest(
            DirectBuffer buffer,
            int offset,
            int length,
            DeferredResponse response);

    long onDataFrame(
            DirectBuffer buffer,
            int offset,
            int length);

}

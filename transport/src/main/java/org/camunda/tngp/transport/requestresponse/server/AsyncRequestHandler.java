package org.camunda.tngp.transport.requestresponse.server;

import uk.co.real_logic.agrona.DirectBuffer;

public interface AsyncRequestHandler
{
    long onRequest(
            DirectBuffer buffer,
            int offset,
            int length,
            DeferredResponse response);

}

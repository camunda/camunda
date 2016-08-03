package org.camunda.tngp.transport.requestresponse.server;

import uk.co.real_logic.agrona.DirectBuffer;

public interface AsyncRequestHandler
{
    long POSTPONE_RESPONSE_CODE = -2L;

    long onRequest(
            DirectBuffer buffer,
            int offset,
            int length,
            DeferredResponse response);

}

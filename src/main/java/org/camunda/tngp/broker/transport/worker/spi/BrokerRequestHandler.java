package org.camunda.tngp.broker.transport.worker.spi;

import org.camunda.tngp.transport.requestresponse.server.DeferredResponse;

import uk.co.real_logic.agrona.DirectBuffer;

public interface BrokerRequestHandler<C extends ResourceContext>
{

    public long onRequest(
             C context,
             DirectBuffer msg,
             int offset,
             int length,
             DeferredResponse response,
             int sbeBlockLength,
             int sbeSchemaVersion);

}

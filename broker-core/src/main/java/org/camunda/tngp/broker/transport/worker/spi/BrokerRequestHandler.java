package org.camunda.tngp.broker.transport.worker.spi;

import org.camunda.tngp.transport.requestresponse.server.DeferredResponse;

import org.agrona.DirectBuffer;

public interface BrokerRequestHandler<C extends ResourceContext>
{

    long onRequest(
             C context,
             DirectBuffer msg,
             int offset,
             int length,
             DeferredResponse response);

}

package org.camunda.tngp.broker.transport.worker.spi;

import org.agrona.DirectBuffer;
import org.camunda.tngp.transport.requestresponse.server.DeferredResponse;

public interface BrokerRequestHandler<C extends ResourceContext>
{

    long onRequest(
             C context,
             DirectBuffer msg,
             int offset,
             int length,
             DeferredResponse response);

    int getTemplateId();

}

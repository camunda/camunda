package org.camunda.tngp.taskqueue.protocol;

import org.camunda.tngp.transport.requestresponse.server.DeferredResponse;

import uk.co.real_logic.agrona.DirectBuffer;

public interface SbeRequestHandler
{

    int getTemplateId();

    public long onRequest(
             DirectBuffer msg,
             int offset,
             int length,
             DeferredResponse response,
             int sbeBlockLength,
             int sbeSchemaVersion);

}

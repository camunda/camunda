package org.camunda.tngp.client.impl.cmd;

import uk.co.real_logic.agrona.DirectBuffer;

public interface ClientResponseHandler<R>
{

    int getResponseSchemaId();

    int getResponseTemplateId();

    R readResponse(DirectBuffer responseBuffer, int offset, int length);

}

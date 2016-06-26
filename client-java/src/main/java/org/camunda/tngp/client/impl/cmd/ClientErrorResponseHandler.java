package org.camunda.tngp.client.impl.cmd;

import org.camunda.tngp.client.cmd.BrokerRequestException;
import org.camunda.tngp.protocol.error.ErrorReader;

import uk.co.real_logic.agrona.DirectBuffer;

public class ClientErrorResponseHandler
{
    protected ErrorReader errorReader = new ErrorReader();

    public Throwable createException(final DirectBuffer responseBuffer, final int offset, final int length)
    {
        errorReader.wrap(responseBuffer, offset, length);

        return new BrokerRequestException(
                errorReader.componentCode(),
                errorReader.detailCode(),
                errorReader.errorMessage());
    }

}

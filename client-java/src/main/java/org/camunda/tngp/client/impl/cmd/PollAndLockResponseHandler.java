package org.camunda.tngp.client.impl.cmd;

import org.agrona.DirectBuffer;
import org.camunda.tngp.client.cmd.LockedTasksBatch;

public class PollAndLockResponseHandler implements ClientResponseHandler<LockedTasksBatch>
{

    @Override
    public int getResponseSchemaId()
    {
        return -1;
    }

    @Override
    public int getResponseTemplateId()
    {
        return -1;
    }

    @Override
    public LockedTasksBatch readResponse(int channelId, DirectBuffer responseBuffer, int offset, int blockLength, int version)
    {
        return null;
    }


}

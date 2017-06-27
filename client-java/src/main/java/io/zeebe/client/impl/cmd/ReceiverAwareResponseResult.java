package io.zeebe.client.impl.cmd;

import io.zeebe.transport.RemoteAddress;

public interface ReceiverAwareResponseResult
{

    void setReceiver(RemoteAddress receiver);

}

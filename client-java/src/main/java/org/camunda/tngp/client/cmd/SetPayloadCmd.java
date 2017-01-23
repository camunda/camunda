package org.camunda.tngp.client.cmd;

import java.io.InputStream;

import org.camunda.tngp.client.ClientCommand;

public interface SetPayloadCmd<R, C extends ClientCommand<R>> extends ClientCommand<R>
{
    C payload(InputStream payload);

    C payload(String payload);
}

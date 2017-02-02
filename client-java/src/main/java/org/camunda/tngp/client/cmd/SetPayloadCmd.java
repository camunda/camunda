package org.camunda.tngp.client.cmd;

import java.io.InputStream;

import org.camunda.tngp.client.ClientCommand;

public interface SetPayloadCmd<R, C extends ClientCommand<R>> extends ClientCommand<R>
{
    /**
     * Set the payload of the command as JSON stream.
     */
    C payload(InputStream payload);

    /**
     * Set the payload of the command as JSON string.
     */
    C payload(String payload);
}

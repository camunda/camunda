package org.camunda.tngp.client.impl;

import org.camunda.tngp.client.impl.cmd.AbstractCmdImpl;

public interface ClientChannelResolver
{

    int getChannelIdForCmd(final AbstractCmdImpl<?> cmd);

}

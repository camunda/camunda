package org.camunda.tngp.client.impl;

import org.camunda.tngp.client.impl.cmd.AbstractCmdImpl;
import org.camunda.tngp.client.impl.cmd.AbstractSingleMessageCmd;

public interface ClientChannelResolver
{

    int getChannelIdForCmd(final AbstractCmdImpl<?> cmd);

    int getChannelIdForCmd(final AbstractSingleMessageCmd cmd);

}

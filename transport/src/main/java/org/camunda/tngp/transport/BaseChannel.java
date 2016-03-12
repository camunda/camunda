package org.camunda.tngp.transport;

import java.util.concurrent.Future;

import org.camunda.tngp.transport.impl.BaseChannelImpl.State;

public interface BaseChannel
{

    int getId();

    Future<Boolean> close();

    State getState();

}
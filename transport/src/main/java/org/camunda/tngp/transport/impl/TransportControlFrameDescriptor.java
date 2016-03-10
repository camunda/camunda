package org.camunda.tngp.transport.impl;

import net.long_running.dispatcher.impl.log.DataFrameDescriptor;

public class TransportControlFrameDescriptor extends DataFrameDescriptor
{

    public static final short TYPE_CONTROL_CLOSE = 100;
    public static final short TYPE_CONTROL_END_OF_STREAM = 101;

}

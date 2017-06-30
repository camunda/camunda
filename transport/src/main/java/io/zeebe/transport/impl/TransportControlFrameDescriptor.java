package io.zeebe.transport.impl;

import io.zeebe.dispatcher.impl.log.DataFrameDescriptor;

public class TransportControlFrameDescriptor extends DataFrameDescriptor
{

    public static final short TYPE_CONTROL_CLOSE = 100;
    public static final short TYPE_CONTROL_END_OF_STREAM = 101;
    public static final short TYPE_CONTROL_KEEP_ALIVE = 102;
    public static final short TYPE_PROTO_CONTROL_FRAME = 103;

}

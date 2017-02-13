package org.camunda.tngp.protocol;

import java.nio.ByteOrder;

public class Protocol
{

    /**
     * The endianness of multibyte values encoded in the protocol. This MUST match the
     * default byte order in the SBE XML schema.
     */
    public static final ByteOrder ENDIANNESS = ByteOrder.LITTLE_ENDIAN;

}

package io.zeebe.protocol;

import java.nio.ByteOrder;

public class Protocol
{

    /**
     * The endianness of multibyte values encoded in the protocol. This MUST match the
     * default byte order in the SBE XML schema.
     */
    public static final ByteOrder ENDIANNESS = ByteOrder.LITTLE_ENDIAN;

    /**
     * The null value of an instant property which indicates that it is not set.
     */
    public static final long INSTANT_NULL_VALUE = Long.MIN_VALUE;

}

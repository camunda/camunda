package org.camunda.tngp.msgpack.spec;

public enum MsgPackType
{

    NIL(true),
    INTEGER(true),
    BOOLEAN(true),
    FLOAT(true),
    ARRAY(false),
    MAP(false),
    BINARY(true),
    STRING(true),
    EXTENSION(true),
    NEVER_USED(true);

    protected boolean isScalar;

    MsgPackType(boolean isScalar)
    {
        this.isScalar = isScalar;
    }

    public boolean isScalar()
    {
        return isScalar;
    }
}

package org.camunda.tngp.example.msgpack.impl;

public enum MsgPackType
{

    NIL(true),
    INTEGER(true),
    BOOLEAN(true),
    FLOAT(true),
    ARRAY(false),
    MAP(false),
    STRING(true),
    ;

    protected boolean isScalar;

    private MsgPackType(boolean isScalar)
    {
        this.isScalar = isScalar;
    }

    public boolean isScalar()
    {
        return isScalar;
    }
}

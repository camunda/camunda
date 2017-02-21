package org.camunda.tngp.client.task.impl;

import org.camunda.tngp.client.impl.data.MsgPackConverter;

public class MsgPackField
{
    protected final MsgPackConverter msgPackConverter = new MsgPackConverter();

    protected String json;
    protected byte[] msgPack;

    public String getAsJson()
    {
        return json;
    }

    public void setJson(String json)
    {
        this.json = json;
    }

    public void setMsgPack(byte[] msgPack)
    {
        this.msgPack = msgPack;
        this.json = this.msgPackConverter.convertToJson(msgPack);
    }

    public byte[] getMsgPack()
    {
        return msgPack;
    }

}

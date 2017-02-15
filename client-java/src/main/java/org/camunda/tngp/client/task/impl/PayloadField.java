package org.camunda.tngp.client.task.impl;

import org.camunda.tngp.client.impl.data.MsgPackConverter;

public class PayloadField
{
    protected final MsgPackConverter msgPackConverter = new MsgPackConverter();

    protected String jsonPayload;
    protected byte[] rawPayload;

    public String getPayloadAsJson()
    {
        return jsonPayload;
    }

    public void setJsonPayload(String jsonPayload)
    {
        this.jsonPayload = jsonPayload;
    }

    public void setRawPayload(byte[] payload)
    {
        this.rawPayload = payload;
        this.jsonPayload = this.msgPackConverter.convertToJson(rawPayload);
    }

}

package org.camunda.tngp.example.msgpack.impl;

import org.agrona.DirectBuffer;

// TODO: the name is not good yet: this is not specific for json path; should be something like visitor or filter
public interface JsonPathOperator
{

    boolean matchesString(MsgPackNavigator context, DirectBuffer buffer, int offset, int length);

}

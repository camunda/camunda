package org.camunda.tngp.msgpack.query;

import org.camunda.tngp.msgpack.spec.MsgPackToken;

public interface MsgPackTokenVisitor
{

    void visitElement(int position, MsgPackToken currentValue);

}

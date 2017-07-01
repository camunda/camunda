package io.zeebe.msgpack.query;

import io.zeebe.msgpack.spec.MsgPackToken;

public interface MsgPackTokenVisitor
{

    void visitElement(int position, MsgPackToken currentValue);

}

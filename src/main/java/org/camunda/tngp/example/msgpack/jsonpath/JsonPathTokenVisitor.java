package org.camunda.tngp.example.msgpack.jsonpath;

import org.agrona.DirectBuffer;

public interface JsonPathTokenVisitor
{

    void visit(JsonPathToken type, DirectBuffer valueBuffer, int valueOffset, int valueLength);

}

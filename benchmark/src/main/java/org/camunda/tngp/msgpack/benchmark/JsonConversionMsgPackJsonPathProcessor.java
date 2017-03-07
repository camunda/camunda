package org.camunda.tngp.msgpack.benchmark;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.msgpack.jsonpath.JsonPathQuery;
import org.camunda.tngp.msgpack.jsonpath.JsonPathQueryCompiler;
import org.camunda.tngp.msgpack.query.MsgPackQueryExecutor;
import org.camunda.tngp.msgpack.query.MsgPackTraverser;
import org.msgpack.core.MessagePack;
import org.msgpack.core.MessageUnpacker;

public class JsonConversionMsgPackJsonPathProcessor implements JsonPathProcessor
{

    protected MsgPackConverter converter = new MsgPackConverter();
    protected JsonPathQueryCompiler queryCompiler = new JsonPathQueryCompiler();
    protected MsgPackTraverser traverser = new MsgPackTraverser();
    protected MsgPackQueryExecutor queryExecutor = new MsgPackQueryExecutor();
    protected UnsafeBuffer msgPackBuffer = new UnsafeBuffer(0, 0);

    @Override
    public String evaluateJsonPath(byte[] json, String jsonPath) throws Exception
    {
        final InputStream jsonStream = new ByteArrayInputStream(json);

        final byte[] msgPack = converter.convertToMsgPack(jsonStream);

        msgPackBuffer.wrap(msgPack);

        final JsonPathQuery query = queryCompiler.compile(jsonPath);
        queryExecutor.init(query.getFilters(), query.getFilterInstances());
        traverser.wrap(msgPackBuffer, 0, msgPackBuffer.capacity());
        traverser.traverse(queryExecutor);
        queryExecutor.moveToResult(0);

        final int resultLength = queryExecutor.currentResultLength();
        final byte[] result = new byte[resultLength];

        System.arraycopy(msgPack, queryExecutor.currentResultPosition(), result, 0, resultLength);
        final MessageUnpacker unpacker = MessagePack.newDefaultUnpacker(result);

        return Integer.toString(unpacker.unpackInt());
    }

}

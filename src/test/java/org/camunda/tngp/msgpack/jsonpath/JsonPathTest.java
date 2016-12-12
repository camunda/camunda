package org.camunda.tngp.msgpack.jsonpath;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.msgpack.query.MsgPackQueryExecutor;
import org.camunda.tngp.msgpack.query.MsgPackTraverser;
import org.junit.Test;
import org.msgpack.core.MessagePack;
import org.msgpack.core.MessageUnpacker;
import org.msgpack.jackson.dataformat.MessagePackFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

public class JsonPathTest
{

    @Test
    public void testJsonPath() throws IOException
    {
        // given
        final Map<String, Object> json = new HashMap<>();
        json.put("foo", "bar");

        final ObjectMapper objectMapper = new ObjectMapper(new MessagePackFactory());
        final byte[] msgPackBytes = objectMapper.writeValueAsBytes(json);
        final UnsafeBuffer buffer = new UnsafeBuffer(msgPackBytes);

        final JsonPathQueryCompiler queryCompiler = new JsonPathQueryCompiler();
        final JsonPathQuery jsonPathQuery = queryCompiler.compile("$.foo");

        final MsgPackQueryExecutor visitor = new MsgPackQueryExecutor();
        visitor.init(jsonPathQuery.getFilters(), jsonPathQuery.getFilterInstances());
        final MsgPackTraverser traverser = new MsgPackTraverser();
        traverser.wrap(buffer, 0, buffer.capacity());

        // when
        traverser.traverse(visitor);

        // then
        assertThat(visitor.numResults()).isEqualTo(1);

        visitor.moveToResult(0);
        final int start = visitor.currentResultPosition();
        final int length = visitor.currentResultLength();
        final MessageUnpacker unpacker = MessagePack.newDefaultUnpacker(msgPackBytes, start, length);

        assertThat(unpacker.unpackString()).isEqualTo("bar");

    }

}

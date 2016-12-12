package org.camunda.tngp.msgpack.jsonpath;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.msgpack.query.MsgPackTokenVisitor;
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
        Map<String, Object> json = new HashMap<>();
        json.put("foo", "bar");

        ObjectMapper objectMapper = new ObjectMapper(new MessagePackFactory());
        byte[] msgPackBytes = objectMapper.writeValueAsBytes(json);
        UnsafeBuffer buffer = new UnsafeBuffer(msgPackBytes);

        JsonPathQueryCompiler queryCompiler = new JsonPathQueryCompiler();
        JsonPathQuery jsonPathQuery = queryCompiler.compile("$.foo");

        MsgPackTokenVisitor visitor = new MsgPackTokenVisitor(jsonPathQuery.getFilters(), jsonPathQuery.getFilterInstances());
        MsgPackTraverser traverser = new MsgPackTraverser();
        traverser.wrap(buffer, 0, buffer.capacity());

        // when
        traverser.traverse(visitor);

        // then
        assertThat(visitor.numResults()).isEqualTo(1);

        visitor.moveToResult(0);
        int start = visitor.currentResultPosition();
        int length = visitor.currentResultLength();
        MessageUnpacker unpacker = MessagePack.newDefaultUnpacker(msgPackBytes, start, length);

        assertThat(unpacker.unpackString()).isEqualTo("bar");

    }

}

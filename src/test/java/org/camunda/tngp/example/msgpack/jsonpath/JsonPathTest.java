package org.camunda.tngp.example.msgpack.jsonpath;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.example.msgpack.impl.ImmutableIntList;
import org.camunda.tngp.example.msgpack.impl.newidea.MsgPackFilter;
import org.camunda.tngp.example.msgpack.impl.newidea.MsgPackTraverser;
import org.camunda.tngp.example.msgpack.impl.newidea.MsgPackTokenVisitor;
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
        ImmutableIntList matchingPositions = visitor.getMatchingPositions();
        assertThat(matchingPositions.getSize()).isEqualTo(2);

        int start = matchingPositions.get(0);
        int end = matchingPositions.get(1);
        MessageUnpacker unpacker = MessagePack.newDefaultUnpacker(
                msgPackBytes, start, end - start);

        assertThat(unpacker.unpackString()).isEqualTo("bar");

    }

}

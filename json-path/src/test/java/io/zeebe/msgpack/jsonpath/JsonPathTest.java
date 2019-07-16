/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.msgpack.jsonpath;

import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.zeebe.msgpack.query.MsgPackQueryExecutor;
import io.zeebe.msgpack.query.MsgPackTraverser;
import io.zeebe.msgpack.spec.MsgPackReader;
import io.zeebe.msgpack.spec.MsgPackToken;
import io.zeebe.msgpack.spec.MsgPackType;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;
import org.msgpack.core.MessagePack;
import org.msgpack.core.MessageUnpacker;
import org.msgpack.jackson.dataformat.MessagePackFactory;

public class JsonPathTest {

  @Test
  public void testJsonPath() throws IOException {
    // given
    final Map<String, Object> json = new HashMap<>();
    json.put("foo", "bar");

    final ObjectMapper objectMapper = new ObjectMapper(new MessagePackFactory());
    final byte[] msgPackBytes = objectMapper.writeValueAsBytes(json);
    final UnsafeBuffer buffer = new UnsafeBuffer(msgPackBytes);

    final JsonPathQueryCompiler queryCompiler = new JsonPathQueryCompiler();
    final JsonPathQuery jsonPathQuery = queryCompiler.compile("foo");

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

  @Test
  public void test() throws JsonProcessingException {
    final Map<String, Object> json = new HashMap<>();
    json.put("foo", "bar");

    final ObjectMapper objectMapper = new ObjectMapper(new MessagePackFactory());
    final byte[] msgPackBytes = objectMapper.writeValueAsBytes(json);
    final UnsafeBuffer buffer = new UnsafeBuffer(msgPackBytes);

    final JsonPathQueryCompiler queryCompiler = new JsonPathQueryCompiler();
    final JsonPathQuery jsonPathQuery = queryCompiler.compile("foo");

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

    final MsgPackReader msgPackReader = new MsgPackReader();
    msgPackReader.wrap(buffer, start, length);

    final MsgPackToken token = msgPackReader.readToken();
    assertThat(token.getType()).isEqualTo(MsgPackType.STRING);
    assertThat(token.getValueBuffer()).isEqualTo(wrapString("bar"));
  }
}

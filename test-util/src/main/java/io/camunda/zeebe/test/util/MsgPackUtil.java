/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.test.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertNotNull;

import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.camunda.zeebe.test.util.collection.MapBuilder;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.function.Consumer;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.msgpack.core.MessageBufferPacker;
import org.msgpack.core.MessagePack;
import org.msgpack.jackson.dataformat.MessagePackFactory;

public final class MsgPackUtil {

  private static final ObjectMapper MSGPACK_MAPPER =
      new ObjectMapper(
          new MessagePackFactory()
              .setStreamReadConstraints(
                  StreamReadConstraints.builder()
                      .maxNumberLength(Integer.MAX_VALUE)
                      .maxNestingDepth(Integer.MAX_VALUE)
                      .maxStringLength(Integer.MAX_VALUE)
                      .build()));

  public static DirectBuffer encodeMsgPack(final CheckedConsumer<MessageBufferPacker> msgWriter) {
    final MessageBufferPacker packer = MessagePack.newDefaultBufferPacker();
    try {
      msgWriter.accept(packer);
      packer.close();
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }
    final byte[] bytes = packer.toByteArray();
    return new UnsafeBuffer(bytes);
  }

  public static DirectBuffer asMsgPack(final String key, final Object value) {
    return asMsgPack(Collections.singletonMap(key, value));
  }

  public static DirectBuffer asMsgPack(final Consumer<MapBuilder<DirectBuffer>> consumer) {
    final DirectBuffer buffer = new UnsafeBuffer(0, 0);
    final MapBuilder<DirectBuffer> builder =
        new MapBuilder<>(buffer, map -> buffer.wrap(asMsgPack(map)));
    consumer.accept(builder);
    return builder.done();
  }

  public static DirectBuffer asMsgPack(final Map<String, Object> map) {
    final ObjectMapper objectMapper = new ObjectMapper(new MessagePackFactory());

    try {
      final byte[] msgPackBytes = objectMapper.writeValueAsBytes(map);

      return new UnsafeBuffer(msgPackBytes);
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static void assertEquality(final byte[] actualMsgPack, final String expectedJson) {
    assertNotNull("actual msg pack is null", actualMsgPack);
    try {
      assertThat(MSGPACK_MAPPER.readTree(actualMsgPack))
          .isEqualTo(JsonUtil.JSON_MAPPER.readTree(expectedJson));
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static void assertEquality(final DirectBuffer actualMsgPack, final String expectedJson) {
    assertNotNull("actual msg pack is null", actualMsgPack);
    final byte[] msgPackArray = new byte[actualMsgPack.capacity()];
    actualMsgPack.getBytes(0, msgPackArray);
    assertEquality(msgPackArray, expectedJson);
  }

  public static void assertEqualityExcluding(
      final DirectBuffer actualMsgPack,
      final String expectedJson,
      final String... excludedProperties) {
    assertNotNull("actual msg pack is null", actualMsgPack);
    final byte[] msgPackArray = new byte[actualMsgPack.capacity()];
    actualMsgPack.getBytes(0, msgPackArray);
    assertEqualityExcluding(msgPackArray, expectedJson, excludedProperties);
  }

  public static void assertEqualityExcluding(
      final byte[] actualMsgPack, final String expectedJson, final String... excludedProperties) {

    assertNotNull("actual msg pack is null", actualMsgPack);

    final JsonNode msgPackNode;
    final JsonNode jsonNode;
    try {
      msgPackNode = MSGPACK_MAPPER.readTree(actualMsgPack);
      jsonNode = JsonUtil.JSON_MAPPER.readTree(expectedJson);
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }

    if (!msgPackNode.isObject() || !jsonNode.isObject()) {
      throw new RuntimeException("both documents must be JSON objects");
    }

    ((ObjectNode) msgPackNode).remove(Arrays.asList(excludedProperties));
    ((ObjectNode) jsonNode).remove(Arrays.asList(excludedProperties));

    assertThat(msgPackNode).isEqualTo(jsonNode);
  }

  public static byte[] asMsgPackReturnArray(final String json) {
    try {
      return MSGPACK_MAPPER.writeValueAsBytes(JsonUtil.JSON_MAPPER.readTree(json));
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static DirectBuffer asMsgPack(final String json) {
    return new UnsafeBuffer(asMsgPackReturnArray(json));
  }

  @FunctionalInterface
  public interface CheckedConsumer<T> {
    void accept(T t) throws Exception;
  }
}

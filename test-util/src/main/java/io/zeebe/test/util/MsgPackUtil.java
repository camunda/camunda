/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.test.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertNotNull;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.zeebe.test.util.collection.MapBuilder;
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

public class MsgPackUtil {

  private static final ObjectMapper MSGPACK_MAPPER = new ObjectMapper(new MessagePackFactory());

  public static DirectBuffer encodeMsgPack(CheckedConsumer<MessageBufferPacker> msgWriter) {
    final MessageBufferPacker packer = MessagePack.newDefaultBufferPacker();
    try {
      msgWriter.accept(packer);
      packer.close();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    final byte[] bytes = packer.toByteArray();
    return new UnsafeBuffer(bytes);
  }

  @FunctionalInterface
  public interface CheckedConsumer<T> {
    void accept(T t) throws Exception;
  }

  public static DirectBuffer asMsgPack(String key, Object value) {
    return asMsgPack(Collections.singletonMap(key, value));
  }

  public static DirectBuffer asMsgPack(Consumer<MapBuilder<DirectBuffer>> consumer) {
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
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static void assertEquality(byte[] actualMsgPack, String expectedJson) {
    assertNotNull("actual msg pack is null", actualMsgPack);
    try {
      assertThat(MSGPACK_MAPPER.readTree(actualMsgPack))
          .isEqualTo(JsonUtil.JSON_MAPPER.readTree(expectedJson));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static void assertEquality(DirectBuffer actualMsgPack, String expectedJson) {
    assertNotNull("actual msg pack is null", actualMsgPack);
    final byte[] msgPackArray = new byte[actualMsgPack.capacity()];
    actualMsgPack.getBytes(0, msgPackArray);
    assertEquality(msgPackArray, expectedJson);
  }

  public static void assertEqualityExcluding(
      DirectBuffer actualMsgPack, String expectedJson, String... excludedProperties) {
    assertNotNull("actual msg pack is null", actualMsgPack);
    final byte[] msgPackArray = new byte[actualMsgPack.capacity()];
    actualMsgPack.getBytes(0, msgPackArray);
    assertEqualityExcluding(msgPackArray, expectedJson, excludedProperties);
  }

  public static void assertEqualityExcluding(
      byte[] actualMsgPack, String expectedJson, String... excludedProperties) {

    assertNotNull("actual msg pack is null", actualMsgPack);

    final JsonNode msgPackNode;
    final JsonNode jsonNode;
    try {
      msgPackNode = MSGPACK_MAPPER.readTree(actualMsgPack);
      jsonNode = JsonUtil.JSON_MAPPER.readTree(expectedJson);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    if (!msgPackNode.isObject() || !jsonNode.isObject()) {
      throw new RuntimeException("both documents must be JSON objects");
    }

    ((ObjectNode) msgPackNode).remove(Arrays.asList(excludedProperties));
    ((ObjectNode) jsonNode).remove(Arrays.asList(excludedProperties));

    assertThat(msgPackNode).isEqualTo(jsonNode);
  }

  public static byte[] asMsgPackReturnArray(String json) {
    try {
      return MSGPACK_MAPPER.writeValueAsBytes(JsonUtil.JSON_MAPPER.readTree(json));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static DirectBuffer asMsgPack(String json) {
    return new UnsafeBuffer(asMsgPackReturnArray(json));
  }
}

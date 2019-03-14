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
package io.zeebe.msgpack.mapping;

import static io.zeebe.msgpack.mapping.MsgPackTreeNodeIdConstructor.JSON_PATH_SEPARATOR;
import static io.zeebe.msgpack.mapping.MsgPackTreeNodeIdConstructor.JSON_PATH_SEPARATOR_END;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.zeebe.msgpack.spec.MsgPackWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.assertj.core.api.AbstractObjectAssert;
import org.msgpack.jackson.dataformat.MessagePackFactory;

public class MappingTestUtil {
  protected static final String NODE_JSON_OBJECT_KEY = "jsonObject";
  protected static final String NODE_TEST_ATTR_KEY = "testAttr";
  protected static final String NODE_STRING_KEY = "string";
  protected static final String NODE_BOOLEAN_KEY = "boolean";
  protected static final String NODE_INTEGER_KEY = "integer";
  protected static final String NODE_LONG_KEY = "long";
  protected static final String NODE_DOUBLE_KEY = "double";
  protected static final String NODE_ARRAY_KEY = "array";

  protected static final String NODE_STRING_VALUE = "value";
  protected static final boolean NODE_BOOLEAN_VALUE = false;
  protected static final int NODE_INTEGER_VALUE = 1024;
  protected static final long NODE_LONG_VALUE = Long.MAX_VALUE;
  protected static final double NODE_DOUBLE_VALUE = 0.3;
  protected static final String NODE_TEST_ATTR_VALUE = "test";
  protected static final Integer[] NODE_ARRAY_VALUE = {0, 1, 2, 3};

  protected static final String NODE_JSON_OBJECT_PATH = "jsonObject";

  protected static final Map<String, Object> JSON_PAYLOAD = new HashMap<>();
  protected static final ObjectMapper MSGPACK_MAPPER = new ObjectMapper(new MessagePackFactory());
  public static final ObjectMapper JSON_MAPPER = new ObjectMapper();
  protected static final byte[] MSG_PACK_BYTES;

  private static final MutableDirectBuffer WRITE_BUFFER = new UnsafeBuffer(new byte[256]);
  private static final MsgPackWriter WRITER = new MsgPackWriter();

  public static Path jsonDocumentPath;

  static {
    JSON_MAPPER.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);

    JSON_PAYLOAD.put(NODE_STRING_KEY, NODE_STRING_VALUE);
    JSON_PAYLOAD.put(NODE_BOOLEAN_KEY, NODE_BOOLEAN_VALUE);
    JSON_PAYLOAD.put(NODE_INTEGER_KEY, NODE_INTEGER_VALUE);
    JSON_PAYLOAD.put(NODE_LONG_KEY, NODE_LONG_VALUE);
    JSON_PAYLOAD.put(NODE_DOUBLE_KEY, NODE_DOUBLE_VALUE);
    JSON_PAYLOAD.put(NODE_ARRAY_KEY, NODE_ARRAY_VALUE);

    final Map<String, Object> jsonObject = new HashMap<>();
    jsonObject.put(NODE_TEST_ATTR_KEY, NODE_TEST_ATTR_VALUE);
    JSON_PAYLOAD.put(NODE_JSON_OBJECT_KEY, jsonObject);

    byte[] bytes = null;
    try {
      bytes = MSGPACK_MAPPER.writeValueAsBytes(JSON_PAYLOAD);
    } catch (JsonProcessingException e) {
      e.printStackTrace();
    } finally {
      MSG_PACK_BYTES = bytes;
    }

    try {
      jsonDocumentPath =
          Paths.get(
              MsgPackDocumentTreeWriterTest.class.getResource("largeJsonDocument.json").toURI());
    } catch (Exception e) {
      jsonDocumentPath = null;
    }
  }

  public static void assertThatIsArrayNode(
      MsgPackTree msgPackTree, String nodeId, String... childs) {
    assertThat(msgPackTree.isArrayNode(nodeId)).isTrue();
    assertChildNodes(msgPackTree, nodeId, childs.length, childs);
  }

  public static void assertThatIsMapNode(MsgPackTree msgPackTree, String nodeId, String... childs) {
    assertThat(msgPackTree.isMapNode(nodeId)).isTrue();
    assertChildNodes(msgPackTree, nodeId, childs.length, childs);
  }

  private static void assertChildNodes(
      MsgPackTree msgPackTree, String nodeId, int childCount, String[] childs) {
    final Set<String> arrayValues = msgPackTree.getChildren(nodeId);
    assertThat(arrayValues.size()).isEqualTo(childCount);
    for (String child : childs) {
      assertThat(arrayValues.contains(child)).isTrue();
    }
  }

  public static void assertThatIsLeafNode(
      MsgPackTree msgPackTree, String leafId, byte[] expectedBytes) {
    assertThat(msgPackTree.isValueNode(leafId)).isTrue();

    WRITER.wrap(WRITE_BUFFER, 0);
    msgPackTree.writeValueNode(WRITER, leafId);

    assertThat(WRITER.getOffset()).isEqualTo(expectedBytes.length);
    assertThat(WRITE_BUFFER.byteArray()).startsWith(expectedBytes);
  }

  public static String constructNodeId(String... nodeNames) {
    final StringBuilder builder = new StringBuilder();
    if (nodeNames.length >= 1) {
      builder.append(nodeNames[0]);

      for (int i = 1; i < nodeNames.length; i++) {
        builder.append(JSON_PATH_SEPARATOR).append(nodeNames[i]).append(JSON_PATH_SEPARATOR_END);
      }
    }
    return builder.toString();
  }

  public static MsgPackAssert assertThatMsgPack(DirectBuffer msgPack) {
    return new MsgPackAssert(msgPack);
  }

  public static class MsgPackAssert extends AbstractObjectAssert<MsgPackAssert, DirectBuffer> {

    public MsgPackAssert(DirectBuffer actual) {
      super(actual, MsgPackAssert.class);
    }

    public MsgPackAssert hasValue(String json) {
      final byte[] actualAsArray = new byte[actual.capacity()];
      actual.getBytes(0, actualAsArray, 0, actualAsArray.length);

      final JsonNode actualTree;
      final JsonNode expectedTree;

      try {
        actualTree = MSGPACK_MAPPER.readTree(actualAsArray);
      } catch (IOException e) {
        failWithMessage("Actual document is not valid msgpack: %s", e.getMessage());
        return this;
      }

      try {
        expectedTree = JSON_MAPPER.readTree(json);
      } catch (IOException e) {
        failWithMessage("Expected document is not valid json: %s", e.getMessage());
        return this;
      }

      if (!actualTree.equals(expectedTree)) {
        failWithMessage(
            "Could not match documents. Expected: %s. Actual: %s", expectedTree, actualTree);
      }

      return this;
    }
  }
}

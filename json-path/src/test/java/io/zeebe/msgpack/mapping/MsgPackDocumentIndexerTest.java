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

import static io.zeebe.msgpack.mapping.MappingTestUtil.JSON_MAPPER;
import static io.zeebe.msgpack.mapping.MappingTestUtil.MSGPACK_MAPPER;
import static io.zeebe.msgpack.mapping.MappingTestUtil.MSG_PACK_BYTES;
import static io.zeebe.msgpack.mapping.MappingTestUtil.NODE_ARRAY_KEY;
import static io.zeebe.msgpack.mapping.MappingTestUtil.NODE_BOOLEAN_KEY;
import static io.zeebe.msgpack.mapping.MappingTestUtil.NODE_BOOLEAN_VALUE;
import static io.zeebe.msgpack.mapping.MappingTestUtil.NODE_DOUBLE_KEY;
import static io.zeebe.msgpack.mapping.MappingTestUtil.NODE_DOUBLE_VALUE;
import static io.zeebe.msgpack.mapping.MappingTestUtil.NODE_INTEGER_KEY;
import static io.zeebe.msgpack.mapping.MappingTestUtil.NODE_INTEGER_VALUE;
import static io.zeebe.msgpack.mapping.MappingTestUtil.NODE_JSON_OBJECT_KEY;
import static io.zeebe.msgpack.mapping.MappingTestUtil.NODE_LONG_KEY;
import static io.zeebe.msgpack.mapping.MappingTestUtil.NODE_LONG_VALUE;
import static io.zeebe.msgpack.mapping.MappingTestUtil.NODE_STRING_KEY;
import static io.zeebe.msgpack.mapping.MappingTestUtil.NODE_STRING_VALUE;
import static io.zeebe.msgpack.mapping.MappingTestUtil.NODE_TEST_ATTR_KEY;
import static io.zeebe.msgpack.mapping.MappingTestUtil.NODE_TEST_ATTR_VALUE;
import static io.zeebe.msgpack.mapping.MappingTestUtil.assertThatIsArrayNode;
import static io.zeebe.msgpack.mapping.MappingTestUtil.assertThatIsLeafNode;
import static io.zeebe.msgpack.mapping.MappingTestUtil.assertThatIsMapNode;
import static io.zeebe.msgpack.mapping.MappingTestUtil.constructNodeId;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

public class MsgPackDocumentIndexerTest {
  private final MsgPackDocumentIndexer indexer = new MsgPackDocumentIndexer();

  @Test
  public void shouldIndexDocument() throws Exception {
    // given document
    final DirectBuffer document = new UnsafeBuffer(MSG_PACK_BYTES);

    // when
    final MsgPackTree documentTree = indexer.index(document);

    // then tree is expected as
    assertThatIsMapNode(
        documentTree,
        "$",
        NODE_STRING_KEY,
        NODE_BOOLEAN_KEY,
        NODE_INTEGER_KEY,
        NODE_LONG_KEY,
        NODE_DOUBLE_KEY,
        NODE_ARRAY_KEY,
        NODE_JSON_OBJECT_KEY);
    assertThatIsMapNode(documentTree, constructNodeId("$", "jsonObject"), NODE_TEST_ATTR_KEY);
    assertThatIsArrayNode(documentTree, constructNodeId("$", "array"), "0", "1", "2", "3");

    assertThatIsLeafNode(
        documentTree,
        constructNodeId("$", "string"),
        MSGPACK_MAPPER.writeValueAsBytes(NODE_STRING_VALUE));
    assertThatIsLeafNode(
        documentTree,
        constructNodeId("$", "boolean"),
        MSGPACK_MAPPER.writeValueAsBytes(NODE_BOOLEAN_VALUE));
    assertThatIsLeafNode(
        documentTree,
        constructNodeId("$", "integer"),
        MSGPACK_MAPPER.writeValueAsBytes(NODE_INTEGER_VALUE));
    assertThatIsLeafNode(
        documentTree,
        constructNodeId("$", "long"),
        MSGPACK_MAPPER.writeValueAsBytes(NODE_LONG_VALUE));
    assertThatIsLeafNode(
        documentTree,
        constructNodeId("$", "double"),
        MSGPACK_MAPPER.writeValueAsBytes(NODE_DOUBLE_VALUE));
    assertThatIsLeafNode(
        documentTree, constructNodeId("$", "array", "0"), MSGPACK_MAPPER.writeValueAsBytes(0));
    assertThatIsLeafNode(
        documentTree, constructNodeId("$", "array", "1"), MSGPACK_MAPPER.writeValueAsBytes(1));
    assertThatIsLeafNode(
        documentTree, constructNodeId("$", "array", "2"), MSGPACK_MAPPER.writeValueAsBytes(2));
    assertThatIsLeafNode(
        documentTree, constructNodeId("$", "array", "3"), MSGPACK_MAPPER.writeValueAsBytes(3));
    assertThatIsLeafNode(
        documentTree,
        constructNodeId("$", "jsonObject", "testAttr"),
        MSGPACK_MAPPER.writeValueAsBytes(NODE_TEST_ATTR_VALUE));
  }

  @Test
  public void shouldIndexDocumentWithMoreArrays() throws Exception {
    // given document
    final String jsonDocument =
        "{'first': { 'range': [0, 2], 'friends': [-1, {'id': 0, 'name': 'Rodriguez Richards'}],"
            + "'greeting': 'Hello, Bauer! You have 7 unread messages.', 'favoriteFruit': 'apple'}}";
    final byte[] msgPackBytes =
        MSGPACK_MAPPER.writeValueAsBytes(JSON_MAPPER.readTree(jsonDocument));
    final DirectBuffer document = new UnsafeBuffer(msgPackBytes);

    // when
    final MsgPackTree documentTree = indexer.index(document);

    // then tree is expected as
    assertThatIsMapNode(documentTree, "$", "first");
    assertThatIsMapNode(
        documentTree,
        constructNodeId("$", "first"),
        "range",
        "friends",
        "greeting",
        "favoriteFruit");

    assertThatIsArrayNode(documentTree, constructNodeId("$", "first", "range"), "0", "1");
    assertThatIsLeafNode(
        documentTree,
        constructNodeId("$", "first", "range", "0"),
        MSGPACK_MAPPER.writeValueAsBytes(0));
    assertThatIsLeafNode(
        documentTree,
        constructNodeId("$", "first", "range", "1"),
        MSGPACK_MAPPER.writeValueAsBytes(2));

    assertThatIsArrayNode(documentTree, constructNodeId("$", "first", "friends"), "0", "1");
    assertThatIsLeafNode(
        documentTree,
        constructNodeId("$", "first", "friends", "0"),
        MSGPACK_MAPPER.writeValueAsBytes(-1));
    assertThatIsMapNode(documentTree, constructNodeId("$", "first", "friends", "1"), "id", "name");
    assertThatIsLeafNode(
        documentTree,
        constructNodeId("$", "first", "friends", "1", "id"),
        MSGPACK_MAPPER.writeValueAsBytes(0));
    assertThatIsLeafNode(
        documentTree,
        constructNodeId("$", "first", "friends", "1", "name"),
        MSGPACK_MAPPER.writeValueAsBytes("Rodriguez Richards"));

    assertThatIsLeafNode(
        documentTree,
        constructNodeId("$", "first", "greeting"),
        MSGPACK_MAPPER.writeValueAsBytes("Hello, Bauer! You have 7 unread messages."));
    assertThatIsLeafNode(
        documentTree,
        constructNodeId("$", "first", "favoriteFruit"),
        MSGPACK_MAPPER.writeValueAsBytes("apple"));
  }

  @Test
  public void shouldIndexDocumentWitObjectArray() throws Exception {
    // given document
    final String jsonDocument =
        "{'friends': [{'id': 0, 'name': 'Rodriguez Richards'}, {'id': 0, 'name': 'Rodriguez Richards'}]}";
    final byte[] msgPackBytes =
        MSGPACK_MAPPER.writeValueAsBytes(JSON_MAPPER.readTree(jsonDocument));
    final DirectBuffer document = new UnsafeBuffer(msgPackBytes);

    // when
    final MsgPackTree documentTree = indexer.index(document);

    // then tree is expected as
    assertThatIsMapNode(documentTree, "$", "friends");
    assertThatIsArrayNode(documentTree, constructNodeId("$", "friends"), "0", "1");

    assertThatIsMapNode(documentTree, constructNodeId("$", "friends", "0"), "id", "name");
    assertThatIsLeafNode(
        documentTree,
        constructNodeId("$", "friends", "0", "id"),
        MSGPACK_MAPPER.writeValueAsBytes(JSON_MAPPER.readTree("0")));
    assertThatIsLeafNode(
        documentTree,
        constructNodeId("$", "friends", "0", "name"),
        MSGPACK_MAPPER.writeValueAsBytes(JSON_MAPPER.readTree("'Rodriguez Richards'")));

    assertThatIsMapNode(documentTree, constructNodeId("$", "friends", "1"), "id", "name");
    assertThatIsLeafNode(
        documentTree,
        constructNodeId("$", "friends", "1", "id"),
        MSGPACK_MAPPER.writeValueAsBytes(JSON_MAPPER.readTree("0")));
    assertThatIsLeafNode(
        documentTree,
        constructNodeId("$", "friends", "1", "name"),
        MSGPACK_MAPPER.writeValueAsBytes(JSON_MAPPER.readTree("'Rodriguez Richards'")));
  }

  @Test
  public void shouldIndexDocumentWitArrayAndObjectWithIndex() throws Exception {
    // given document
    final String jsonDocument = "{'a':['foo'], 'a0':{'b':'c'}}";
    final byte[] msgPackBytes =
        MSGPACK_MAPPER.writeValueAsBytes(JSON_MAPPER.readTree(jsonDocument));
    final DirectBuffer document = new UnsafeBuffer(msgPackBytes);

    // when
    final MsgPackTree documentTree = indexer.index(document);

    // then tree is expected as
    assertThatIsMapNode(documentTree, "$", "a", "a0");

    assertThatIsArrayNode(documentTree, constructNodeId("$", "a"), "0");
    assertThatIsLeafNode(
        documentTree, constructNodeId("$", "a", "0"), MSGPACK_MAPPER.writeValueAsBytes("foo"));

    assertThatIsMapNode(documentTree, constructNodeId("$", "a0"), "b");
    assertThatIsLeafNode(
        documentTree, constructNodeId("$", "a0", "b"), MSGPACK_MAPPER.writeValueAsBytes("c"));
  }
}

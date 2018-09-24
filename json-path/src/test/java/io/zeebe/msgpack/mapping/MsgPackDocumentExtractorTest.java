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

import static io.zeebe.msgpack.mapping.MappingBuilder.createMapping;
import static io.zeebe.msgpack.mapping.MappingTestUtil.JSON_MAPPER;
import static io.zeebe.msgpack.mapping.MappingTestUtil.MSGPACK_MAPPER;
import static io.zeebe.msgpack.mapping.MappingTestUtil.MSG_PACK_BYTES;
import static io.zeebe.msgpack.mapping.MappingTestUtil.assertThatIsArrayNode;
import static io.zeebe.msgpack.mapping.MappingTestUtil.assertThatIsLeafNode;
import static io.zeebe.msgpack.mapping.MappingTestUtil.assertThatIsMapNode;
import static io.zeebe.msgpack.mapping.MappingTestUtil.constructNodeId;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class MsgPackDocumentExtractorTest {
  private final MsgPackDocumentExtractor extractor = new MsgPackDocumentExtractor();

  @Rule public ExpectedException expectedException = ExpectedException.none();

  @Test
  public void shouldExtractEntireDocument() throws Exception {
    // given document
    final DirectBuffer document = new UnsafeBuffer(MSG_PACK_BYTES);
    final Mapping[] mapping = createMapping("$", "$");
    final MsgPackTree extractTree = new MsgPackTree();

    // when
    final MsgPackDiff diff = extractor.extract(document, true, mapping);
    diff.mergeInto(extractTree);

    // then root is leaf
    assertThatIsLeafNode(extractTree, "$", MSG_PACK_BYTES);
  }

  @Test
  public void shouldExtractEntireDocumentAndCreateNewObject() throws Exception {
    // given document
    final DirectBuffer document = new UnsafeBuffer(MSG_PACK_BYTES);
    final Mapping[] mapping = createMapping("$", "$.old");
    final MsgPackTree extractTree = new MsgPackTree();

    // when
    final MsgPackDiff diff = extractor.extract(document, true, mapping);
    diff.mergeInto(extractTree);

    // then
    assertThatIsMapNode(extractTree, "$", "old");
    assertThatIsLeafNode(extractTree, constructNodeId("$", "old"), MSG_PACK_BYTES);
  }

  @Test
  public void shouldExtractEntireDocumentAndCreateNewDeepObject() throws Exception {
    // given document
    final DirectBuffer document = new UnsafeBuffer(MSG_PACK_BYTES);
    final Mapping[] mapping = createMapping("$", "$.old.test");
    final MsgPackTree extractTree = new MsgPackTree();

    // when
    final MsgPackDiff diff = extractor.extract(document, true, mapping);
    diff.mergeInto(extractTree);

    // then
    assertThatIsMapNode(extractTree, "$", "old");
    assertThatIsMapNode(extractTree, constructNodeId("$", "old"), "test");
    assertThatIsLeafNode(extractTree, constructNodeId("$", "old", "test"), MSG_PACK_BYTES);
  }

  @Test
  public void shouldCreateOrRenameObject() throws Exception {
    // given document
    final DirectBuffer document = new UnsafeBuffer(MSG_PACK_BYTES);
    final Mapping[] mapping = createMapping("$.jsonObject", "$.testObj");
    final MsgPackTree extractTree = new MsgPackTree();

    // when
    final MsgPackDiff diff = extractor.extract(document, true, mapping);
    diff.mergeInto(extractTree);

    // then
    assertThatIsMapNode(extractTree, "$", "testObj");

    // and leaf is expected as
    final Map<String, Object> json = new HashMap<>();
    json.put("testAttr", "test");
    final byte[] bytes = MSGPACK_MAPPER.writeValueAsBytes(json);
    assertThatIsLeafNode(extractTree, constructNodeId("$", "testObj"), bytes);
  }

  @Test
  public void shouldCreateObjectOnRoot() throws Exception {
    // given document
    final DirectBuffer document = new UnsafeBuffer(MSG_PACK_BYTES);
    final Mapping[] mapping = createMapping("$.jsonObject", "$");
    final MsgPackTree extractTree = new MsgPackTree();

    // when
    final MsgPackDiff diff = extractor.extract(document, true, mapping);
    diff.mergeInto(extractTree);

    // then extractTree root is leaf
    assertThat(extractTree.isValueNode("$")).isTrue();

    // and value is expected as
    final Map<String, Object> json = new HashMap<>();
    json.put("testAttr", "test");
    final byte[] bytes = MSGPACK_MAPPER.writeValueAsBytes(json);
    assertThatIsLeafNode(extractTree, "$", bytes);
  }

  @Test
  public void shouldCreateValueOnArrayIndex() throws Exception {
    // given document
    final DirectBuffer document = new UnsafeBuffer(MSG_PACK_BYTES);
    final Mapping[] mapping = createMapping("$.array[1]", "$.array[0]");
    final MsgPackTree extractTree = new MsgPackTree();

    // when
    final MsgPackDiff diff = extractor.extract(document, true, mapping);
    diff.mergeInto(extractTree);

    // then extractTree contains root node
    assertThatIsMapNode(extractTree, "$", "array");

    // and array node
    assertThatIsArrayNode(extractTree, constructNodeId("$", "array"), "0");

    // and value is
    assertThatIsLeafNode(
        extractTree, constructNodeId("$", "array", "0"), MSGPACK_MAPPER.writeValueAsBytes(1));
  }

  @Test
  public void shouldCreateValueOnArrayIndexObject() throws Exception {
    // given document
    final DirectBuffer document = new UnsafeBuffer(MSG_PACK_BYTES);
    final Mapping[] mapping = createMapping("$.array[1]", "$.array[0].test");
    final MsgPackTree extractTree = new MsgPackTree();

    // when
    final MsgPackDiff diff = extractor.extract(document, true, mapping);
    diff.mergeInto(extractTree);

    // then extractTree contains root node
    assertThatIsMapNode(extractTree, "$", "array");

    // and array node
    assertThatIsArrayNode(extractTree, constructNodeId("$", "array"), "0");
    assertThatIsMapNode(extractTree, constructNodeId("$", "array", "0"), "test");

    // and value is
    assertThatIsLeafNode(
        extractTree,
        constructNodeId("$", "array", "0", "test"),
        MSGPACK_MAPPER.writeValueAsBytes(1));
  }

  @Test
  public void shouldExtractWithMoreMappings() throws Exception {
    // given document
    final DirectBuffer document = new UnsafeBuffer(MSG_PACK_BYTES);
    final Mapping[] mappings =
        MappingBuilder.createMappings()
            .mapping("$.boolean", "$.newBoolean")
            .mapping("$.array", "$.newArray")
            .mapping("$.jsonObject", "$.newObject")
            .build();
    final MsgPackTree extractTree = new MsgPackTree();

    // when
    final MsgPackDiff diff = extractor.extract(document, true, mappings);
    diff.mergeInto(extractTree);

    // then root is
    assertThatIsMapNode(extractTree, "$", "newBoolean", "newArray", "newObject");

    // and new bool is expected as
    assertThatIsLeafNode(
        extractTree, constructNodeId("$", "newBoolean"), MSGPACK_MAPPER.writeValueAsBytes(false));

    // and new array is expected as
    byte[] bytes = MSGPACK_MAPPER.writeValueAsBytes(new int[] {0, 1, 2, 3});
    assertThatIsLeafNode(extractTree, constructNodeId("$", "newArray"), bytes);

    // and new object is expected as
    final Map<String, Object> jsonObject = new HashMap<>();
    jsonObject.put("testAttr", "test");
    bytes = MSGPACK_MAPPER.writeValueAsBytes(jsonObject);
    assertThatIsLeafNode(extractTree, constructNodeId("$", "newObject"), bytes);
  }

  @Test
  public void shouldThrowExceptionIfMappingMatchesTwiceInStrictMode() throws Exception {
    // given document
    final DirectBuffer document =
        new UnsafeBuffer(
            MSGPACK_MAPPER.writeValueAsBytes(JSON_MAPPER.readTree("{'foo':'bar', 'foa':'baz'}")));
    final Mapping[] mapping = createMapping("$.*", "$");

    // expect
    expectedException.expect(IllegalStateException.class);
    expectedException.expectMessage("JSON path mapping has more than one matching source.");

    // when
    extractor.extract(document, true, mapping);
  }
}

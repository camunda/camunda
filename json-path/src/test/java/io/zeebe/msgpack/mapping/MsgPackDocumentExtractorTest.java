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

import static org.assertj.core.api.Assertions.assertThat;
import static io.zeebe.msgpack.mapping.MappingBuilder.createMapping;
import static io.zeebe.msgpack.mapping.MappingTestUtil.*;

import java.util.HashMap;
import java.util.Map;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class MsgPackDocumentExtractorTest
{
    private final MsgPackDocumentExtractor extractor = new MsgPackDocumentExtractor();

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void shouldExtractHoleDocument() throws Exception
    {
        // given document
        final DirectBuffer document = new UnsafeBuffer(MSG_PACK_BYTES);
        extractor.wrap(document);
        final Mapping[] mapping = createMapping("$", "$");

        // when
        final MsgPackTree extractTree = extractor.extract(mapping);

        // then root is leaf
        assertThatIsLeafNode(extractTree, "$", MSG_PACK_BYTES);
    }

    @Test
    public void shouldExtractHoleDocumentAndCreateNewObject() throws Exception
    {
        // given document
        final DirectBuffer document = new UnsafeBuffer(MSG_PACK_BYTES);
        extractor.wrap(document);
        final Mapping[] mapping = createMapping("$", "$.old");

        // when
        final MsgPackTree extractTree = extractor.extract(mapping);

        // then
        assertThatIsMapNode(extractTree, "$", "old");
        assertThatIsLeafNode(extractTree, "$.old", MSG_PACK_BYTES);
    }

    @Test
    public void shouldExtractHoleDocumentAndCreateNewDeepObject() throws Exception
    {
        // given document
        final DirectBuffer document = new UnsafeBuffer(MSG_PACK_BYTES);
        extractor.wrap(document);
        final Mapping[] mapping = createMapping("$", "$.old.test");

        // when
        final MsgPackTree extractTree = extractor.extract(mapping);

        // then
        assertThatIsMapNode(extractTree, "$", "old");
        assertThatIsMapNode(extractTree, "$.old", "test");
        assertThatIsLeafNode(extractTree, "$.old.test", MSG_PACK_BYTES);
    }

    @Test
    public void shouldCreateOrRenameObject() throws Exception
    {
        // given document
        final DirectBuffer document = new UnsafeBuffer(MSG_PACK_BYTES);
        extractor.wrap(document);
        final Mapping[] mapping = createMapping("$.jsonObject", "$.testObj");

        // when
        final MsgPackTree extractTree = extractor.extract(mapping);

        // then
        assertThatIsMapNode(extractTree, "$", "testObj");

        // and leaf is expected as
        final Map<String, Object> json = new HashMap<>();
        json.put("testAttr", "test");
        final byte[] bytes = MSGPACK_MAPPER.writeValueAsBytes(json);
        assertThatIsLeafNode(extractTree, "$.testObj", bytes);
    }

    @Test
    public void shouldCreateObjectOnRoot() throws Exception
    {
        // given document
        final DirectBuffer document = new UnsafeBuffer(MSG_PACK_BYTES);
        extractor.wrap(document);
        final Mapping[] mapping = createMapping("$.jsonObject", "$");

        // when
        final MsgPackTree extractTree = extractor.extract(mapping);

        // then tree root is leaf
        assertThat(extractTree.isLeaf("$")).isTrue();

        // and value is expected as
        final Map<String, Object> json = new HashMap<>();
        json.put("testAttr", "test");
        final byte[] bytes = MSGPACK_MAPPER.writeValueAsBytes(json);
        assertThatIsLeafNode(extractTree, "$", bytes);
    }

    @Test
    public void shouldCreateValueOnArrayIndex() throws Exception
    {
        // given document
        final DirectBuffer document = new UnsafeBuffer(MSG_PACK_BYTES);
        extractor.wrap(document);
        final Mapping[] mapping = createMapping("$.array[1]", "$.array[0]");

        // when
        final MsgPackTree extractTree = extractor.extract(mapping);

        // then tree contains root node
        assertThatIsMapNode(extractTree, "$", "array");

        // and array node
        assertThatIsArrayNode(extractTree, "$.array", "0");

        // and value is
        assertThatIsLeafNode(extractTree, "$.array.0", MSGPACK_MAPPER.writeValueAsBytes(1));
    }

    @Test
    public void shouldCreateValueOnArrayIndexObject() throws Exception
    {
        // given document
        final DirectBuffer document = new UnsafeBuffer(MSG_PACK_BYTES);
        extractor.wrap(document);
        final Mapping[] mapping = createMapping("$.array[1]", "$.array[0].test");

        // when
        final MsgPackTree extractTree = extractor.extract(mapping);

        // then tree contains root node
        assertThatIsMapNode(extractTree, "$", "array");

        // and array node
        assertThatIsArrayNode(extractTree, "$.array", "0");
        assertThatIsMapNode(extractTree, "$.array.0", "test");

        // and value is
        assertThatIsLeafNode(extractTree, "$.array.0.test", MSGPACK_MAPPER.writeValueAsBytes(1));
    }

    @Test
    public void shouldExtractWithMoreMappings() throws Exception
    {
        // given document
        final DirectBuffer document = new UnsafeBuffer(MSG_PACK_BYTES);
        extractor.wrap(document);
        final Mapping[] mappings = MappingBuilder.createMappings()
                                        .mapping("$.boolean", "$.newBoolean")
                                        .mapping("$.array", "$.newArray")
                                        .mapping("$.jsonObject", "$.newObject")
                                        .build();

        // when
        final MsgPackTree extractTree = extractor.extract(mappings);

        // then root is
        assertThatIsMapNode(extractTree, "$", "newBoolean", "newArray", "newObject");

        // and new bool is expected as
        assertThatIsLeafNode(extractTree, "$.newBoolean", MSGPACK_MAPPER.writeValueAsBytes(false));

        // and new array is expected as
        byte[] bytes = MSGPACK_MAPPER.writeValueAsBytes(new int[]{0, 1, 2, 3});
        assertThatIsLeafNode(extractTree, "$.newArray", bytes);

        // and new object is expected as
        final Map<String, Object> jsonObject = new HashMap<>();
        jsonObject.put("testAttr", "test");
        bytes = MSGPACK_MAPPER.writeValueAsBytes(jsonObject);
        assertThatIsLeafNode(extractTree, "$.newObject", bytes);
    }

    @Test
    public void shouldThrowExceptionIfMappingMatchesTwice() throws Exception
    {
        // given document
        final DirectBuffer document = new UnsafeBuffer(
            MSGPACK_MAPPER.writeValueAsBytes(JSON_MAPPER.readTree("{'foo':'bar', 'foa':'baz'}")));
        extractor.wrap(document);
        final Mapping[] mapping = createMapping("$.*", "$");

        // expect
        expectedException.expect(IllegalStateException.class);
        expectedException.expectMessage("JSON path mapping has more than one matching source.");

        // when
        extractor.extract(mapping);
    }
}

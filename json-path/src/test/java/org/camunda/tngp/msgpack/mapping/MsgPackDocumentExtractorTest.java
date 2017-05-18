package org.camunda.tngp.msgpack.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.tngp.msgpack.mapping.MappingTestUtil.*;

import java.util.HashMap;
import java.util.Map;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

public class MsgPackDocumentExtractorTest
{
    private final MsgPackDocumentExtractor extractor = new MsgPackDocumentExtractor();

    @Test
    public void shouldExtractHoleDocument() throws Exception
    {
        // given document
        final DirectBuffer document = new UnsafeBuffer(MSG_PACK_BYTES);
        extractor.wrap(document);
        final Mapping mapping = MappingBuilder.createMapping("$", "$");

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
        final Mapping mapping = MappingBuilder.createMapping("$", "$.old");

        // when
        final MsgPackTree extractTree = extractor.extract(mapping);

        // then
        assertThatIsMapNode(extractTree, "$", 1, "old");
        assertThatIsLeafNode(extractTree, "$old", MSG_PACK_BYTES);
    }

    @Test
    public void shouldExtractHoleDocumentAndCreateNewDeepObject() throws Exception
    {
        // given document
        final DirectBuffer document = new UnsafeBuffer(MSG_PACK_BYTES);
        extractor.wrap(document);
        final Mapping mapping = MappingBuilder.createMapping("$", "$.old.test");

        // when
        final MsgPackTree extractTree = extractor.extract(mapping);

        // then
        assertThatIsMapNode(extractTree, "$", 1, "old");
        assertThatIsMapNode(extractTree, "$old", 1, "test");
        assertThatIsLeafNode(extractTree, "$oldtest", MSG_PACK_BYTES);
    }

    @Test
    public void shouldCreateOrRenameObject() throws Exception
    {
        // given document
        final DirectBuffer document = new UnsafeBuffer(MSG_PACK_BYTES);
        extractor.wrap(document);
        final Mapping mapping = MappingBuilder.createMapping("$.jsonObject", "$.testObj");

        // when
        final MsgPackTree extractTree = extractor.extract(mapping);

        // then
        assertThatIsMapNode(extractTree, "$", 1, "testObj");

        // and leaf is expected as
        final Map<String, Object> json = new HashMap<>();
        json.put("testAttr", "test");
        final byte[] bytes = OBJECT_MAPPER.writeValueAsBytes(json);
        assertThatIsLeafNode(extractTree, "$testObj", bytes);
    }

    @Test
    public void shouldCreateObjectOnRoot() throws Exception
    {
        // given document
        final DirectBuffer document = new UnsafeBuffer(MSG_PACK_BYTES);
        extractor.wrap(document);
        final Mapping mapping = MappingBuilder.createMapping("$.jsonObject", "$");

        // when
        final MsgPackTree extractTree = extractor.extract(mapping);

        // then tree root is leaf
        assertThat(extractTree.isLeaf("$")).isTrue();

        // and value is expected as
        final Map<String, Object> json = new HashMap<>();
        json.put("testAttr", "test");
        final byte[] bytes = OBJECT_MAPPER.writeValueAsBytes(json);
        assertThatIsLeafNode(extractTree, "$", bytes);
    }

    @Test
    public void shouldCreateValueOnArrayIndex() throws Exception
    {
        // given document
        final DirectBuffer document = new UnsafeBuffer(MSG_PACK_BYTES);
        extractor.wrap(document);
        final Mapping mapping = MappingBuilder.createMapping("$.array[1]", "$.array[0]");

        // when
        final MsgPackTree extractTree = extractor.extract(mapping);

        // then tree contains root node
        assertThatIsMapNode(extractTree, "$", 1, "array");

        // and array node
        assertThatIsArrayNode(extractTree, "$array", 1, "0");

        // and value is
        assertThatIsLeafNode(extractTree, "$array0", OBJECT_MAPPER.writeValueAsBytes(1));
    }

    @Test
    public void shouldCreateValueOnArrayIndexObject() throws Exception
    {
        // given document
        final DirectBuffer document = new UnsafeBuffer(MSG_PACK_BYTES);
        extractor.wrap(document);
        final Mapping mapping = MappingBuilder.createMapping("$.array[1]", "$.array[0].test");

        // when
        final MsgPackTree extractTree = extractor.extract(mapping);

        // then tree contains root node
        assertThatIsMapNode(extractTree, "$", 1, "array");

        // and array node
        assertThatIsArrayNode(extractTree, "$array", 1, "0");
        assertThatIsMapNode(extractTree, "$array0", 1, "test");

        // and value is
        assertThatIsLeafNode(extractTree, "$array0test", OBJECT_MAPPER.writeValueAsBytes(1));
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
        assertThatIsMapNode(extractTree, "$", 3, "newBoolean", "newArray", "newObject");

        // and new bool is expected as
        assertThatIsLeafNode(extractTree, "$newBoolean", OBJECT_MAPPER.writeValueAsBytes(false));

        // and new array is expected as
        byte[] bytes = OBJECT_MAPPER.writeValueAsBytes(new int[]{0, 1, 2, 3});
        assertThatIsLeafNode(extractTree, "$newArray", bytes);

        // and new object is expected as
        final Map<String, Object> jsonObject = new HashMap<>();
        jsonObject.put("testAttr", "test");
        bytes = OBJECT_MAPPER.writeValueAsBytes(jsonObject);
        assertThatIsLeafNode(extractTree, "$newObject", bytes);
    }
}

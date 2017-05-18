package org.camunda.tngp.msgpack.mapping;

import static org.camunda.tngp.msgpack.mapping.MappingTestUtil.*;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

public class MsgPackDocumentIndexerTest
{
    private final MsgPackDocumentIndexer indexer = new MsgPackDocumentIndexer();

    @Test
    public void shouldIndexDocument() throws Exception
    {
        // given document
        final DirectBuffer document = new UnsafeBuffer(MSG_PACK_BYTES);
        indexer.wrap(document);

        // when
        final MsgPackTree documentTree = indexer.index();

        // then tree is expected as
        assertThatIsMapNode(documentTree, "$", 7, NODE_STRING_KEY, NODE_BOOLEAN_KEY, NODE_INTEGER_KEY,
                                                                   NODE_LONG_KEY, NODE_DOUBLE_KEY, NODE_ARRAY_KEY,
                                                                   NODE_JSON_OBJECT_KEY);
        assertThatIsMapNode(documentTree, "$jsonObject", 1, NODE_TEST_ATTR_KEY);
        assertThatIsArrayNode(documentTree, "$array", 4, "0", "1", "2", "3");

        assertThatIsLeafNode(documentTree, "$string", OBJECT_MAPPER.writeValueAsBytes(NODE_STRING_VALUE));
        assertThatIsLeafNode(documentTree, "$boolean", OBJECT_MAPPER.writeValueAsBytes(NODE_BOOLEAN_VALUE));
        assertThatIsLeafNode(documentTree, "$integer", OBJECT_MAPPER.writeValueAsBytes(NODE_INTEGER_VALUE));
        assertThatIsLeafNode(documentTree, "$long", OBJECT_MAPPER.writeValueAsBytes(NODE_LONG_VALUE));
        assertThatIsLeafNode(documentTree, "$double", OBJECT_MAPPER.writeValueAsBytes(NODE_DOUBLE_VALUE));
        assertThatIsLeafNode(documentTree, "$array0", OBJECT_MAPPER.writeValueAsBytes(0));
        assertThatIsLeafNode(documentTree, "$array1", OBJECT_MAPPER.writeValueAsBytes(1));
        assertThatIsLeafNode(documentTree, "$array2", OBJECT_MAPPER.writeValueAsBytes(2));
        assertThatIsLeafNode(documentTree, "$array3", OBJECT_MAPPER.writeValueAsBytes(3));
        assertThatIsLeafNode(documentTree, "$jsonObjecttestAttr", OBJECT_MAPPER.writeValueAsBytes(NODE_TEST_ATTR_VALUE));
    }
}

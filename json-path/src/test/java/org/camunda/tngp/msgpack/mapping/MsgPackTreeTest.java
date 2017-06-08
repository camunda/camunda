package org.camunda.tngp.msgpack.mapping;

import static org.camunda.tngp.msgpack.mapping.MappingTestUtil.*;

import java.util.HashMap;
import java.util.Map;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.msgpack.jsonpath.JsonPathQuery;
import org.camunda.tngp.msgpack.jsonpath.JsonPathQueryCompiler;
import org.camunda.tngp.msgpack.query.MsgPackQueryExecutor;
import org.camunda.tngp.msgpack.query.MsgPackTraverser;
import org.junit.Test;

/**
 *
 */
public class MsgPackTreeTest
{
    private final MsgPackTree msgPackTree = new MsgPackTree();

    @Test
    public void shouldUseUnderlyingDocument()
    {
        // given
        final DirectBuffer documentBuffer = new UnsafeBuffer(MSG_PACK_BYTES);
        msgPackTree.wrap(documentBuffer);

        // when
        msgPackTree.addLeafNode("$", 0, MSG_PACK_BYTES.length);

        // then
        assertThatIsLeafNode(msgPackTree, "$", MSG_PACK_BYTES);
    }

    @Test
    public void shouldDifferBetweenUnderlyingAndExtractDocument() throws Exception
    {
        // given
        final DirectBuffer documentBuffer = new UnsafeBuffer(MSG_PACK_BYTES);
        msgPackTree.wrap(documentBuffer);
        msgPackTree.addLeafNode("$underlying", 0, MSG_PACK_BYTES.length);

        // when
        final Map<String, Object> jsonMap = new HashMap<>();
        final Map<String, Object> innerMap = new HashMap<>();
        innerMap.put("test", "test");
        jsonMap.put("aObject", innerMap);
        jsonMap.put("string", "stringValue");

        final byte[] bytes = MSGPACK_MAPPER.writeValueAsBytes(jsonMap);
        final DirectBuffer extractBuffer = new UnsafeBuffer(bytes);
        msgPackTree.setExtractDocument(extractBuffer);

        final JsonPathQueryCompiler compiler = new JsonPathQueryCompiler();
        final JsonPathQuery jsonPathQuery = compiler.compile("$.aObject");
        final MsgPackQueryExecutor queryExecutor = new MsgPackQueryExecutor();
        queryExecutor.init(jsonPathQuery.getFilters(), jsonPathQuery.getFilterInstances());
        final MsgPackTraverser traverser = new MsgPackTraverser();
        traverser.wrap(extractBuffer, 0, bytes.length);
        traverser.traverse(queryExecutor);
        queryExecutor.moveToResult(0);

        msgPackTree.addLeafNode("$extract", queryExecutor.currentResultPosition(), queryExecutor.currentResultLength());

        // then
        assertThatIsLeafNode(msgPackTree, "$underlying", MSG_PACK_BYTES);

        // and
        assertThatIsLeafNode(msgPackTree, "$extract", MSGPACK_MAPPER.writeValueAsBytes(innerMap));
    }
}

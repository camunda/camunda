/**
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

import static io.zeebe.msgpack.mapping.MappingTestUtil.*;

import java.util.HashMap;
import java.util.Map;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import io.zeebe.msgpack.jsonpath.JsonPathQuery;
import io.zeebe.msgpack.jsonpath.JsonPathQueryCompiler;
import io.zeebe.msgpack.query.MsgPackQueryExecutor;
import io.zeebe.msgpack.query.MsgPackTraverser;
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

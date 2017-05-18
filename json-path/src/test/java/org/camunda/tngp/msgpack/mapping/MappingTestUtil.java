package org.camunda.tngp.msgpack.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.msgpack.spec.MsgPackWriter;
import org.msgpack.jackson.dataformat.MessagePackFactory;

public class MappingTestUtil
{
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

    protected static final String NODE_STRING_PATH = "$.string";
    protected static final String NODE_JSON_OBJECT_PATH = "$.jsonObject";
    protected static final String NODE_ARRAY_PATH = "$.array";
    protected static final String NODE_ARRAY_FIRST_IDX_PATH = "$.array[0]";
    protected static final String NODE_ROOT_PATH = "$";


    protected static final Map<String, Object> JSON_PAYLOAD = new HashMap<>();
    protected static final ObjectMapper OBJECT_MAPPER = new ObjectMapper(new MessagePackFactory());
    protected static final byte[] MSG_PACK_BYTES;

    private static final MutableDirectBuffer WRITE_BUFFER = new UnsafeBuffer(new byte[256]);
    private static final MsgPackWriter WRITER = new MsgPackWriter();

    static
    {
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
        try
        {
            bytes = OBJECT_MAPPER.writeValueAsBytes(JSON_PAYLOAD);
        }
        catch (JsonProcessingException e)
        {
            e.printStackTrace();
        }
        finally
        {
            MSG_PACK_BYTES = bytes;
        }
    }


    protected static void assertThatNodeContainsTheStartingPayload(JsonNode jsonNode)
    {
        assertThat(jsonNode).isNotNull().isNotEmpty();
        final JsonNode fooNode = jsonNode.get(NODE_STRING_KEY);
        assertThat(fooNode.textValue()).isEqualTo(NODE_STRING_VALUE);

        final JsonNode booleanNode = jsonNode.get(NODE_BOOLEAN_KEY);
        assertThat(booleanNode.booleanValue()).isEqualTo(NODE_BOOLEAN_VALUE);

        final JsonNode integerNode = jsonNode.get(NODE_INTEGER_KEY);
        assertThat(integerNode.intValue()).isEqualTo(NODE_INTEGER_VALUE);

        final JsonNode longNode = jsonNode.get(NODE_LONG_KEY);
        assertThat(longNode.longValue()).isEqualTo(NODE_LONG_VALUE);

        final JsonNode doubleNode = jsonNode.get(NODE_DOUBLE_KEY);
        assertThat(doubleNode.doubleValue()).isEqualTo(NODE_DOUBLE_VALUE);

        final JsonNode objNode = jsonNode.get(NODE_JSON_OBJECT_KEY);
        assertThat(objNode.isObject()).isTrue();
        assertThat(objNode.get(NODE_TEST_ATTR_KEY).textValue()).isEqualTo(NODE_TEST_ATTR_VALUE);

        final JsonNode arrayNode = jsonNode.get(NODE_ARRAY_KEY);
        assertThatNodeContainsStartingArray(arrayNode);
    }

    protected static void assertThatNodeContainsStartingArray(JsonNode newArrayNode)
    {
        assertThat(newArrayNode.isArray()).isTrue();
        assertThat(newArrayNode.get(0).intValue()).isEqualTo(0);
        assertThat(newArrayNode.get(1).intValue()).isEqualTo(1);
        assertThat(newArrayNode.get(2).intValue()).isEqualTo(2);
        assertThat(newArrayNode.get(3).intValue()).isEqualTo(3);
    }

    public static void assertThatIsArrayNode(MsgPackTree msgPackTree, String nodeId, int childCount, String ...childs)
    {
        assertThat(msgPackTree.isArrayNode(nodeId)).isTrue();
        assertChildNodes(msgPackTree, nodeId, childCount, childs);
    }

    public static void assertThatIsMapNode(MsgPackTree msgPackTree, String nodeId, int childCount, String ...childs)
    {
        assertThat(msgPackTree.isMapNode(nodeId)).isTrue();
        assertChildNodes(msgPackTree, nodeId, childCount, childs);
    }

    private static void assertChildNodes(MsgPackTree msgPackTree, String nodeId, int childCount, String[] childs)
    {
        final Set<String> arrayValues = msgPackTree.getChilds(nodeId);
        assertThat(arrayValues.size()).isEqualTo(childCount);
        for (String child : childs)
        {
            assertThat(arrayValues.contains(child)).isTrue();
        }
    }

    public static void assertThatIsLeafNode(MsgPackTree msgPackTree, String leafId, byte[] expectedBytes)
    {
        assertThat(msgPackTree.isLeaf(leafId)).isTrue();

        WRITER.wrap(WRITE_BUFFER, 0);
        msgPackTree.writeLeafMapping(WRITER, leafId);

        assertThat(WRITER.getOffset()).isEqualTo(expectedBytes.length);
        assertThat(WRITE_BUFFER.byteArray()).startsWith(expectedBytes);
    }
}

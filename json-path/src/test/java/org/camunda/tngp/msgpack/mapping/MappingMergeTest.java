package org.camunda.tngp.msgpack.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.tngp.msgpack.mapping.MappingBuilder.createMapping;
import static org.camunda.tngp.msgpack.mapping.MappingBuilder.createMappings;
import static org.camunda.tngp.msgpack.mapping.MappingProcessor.EXCEPTION_MSG_ROOT_MAPPING_IN_COMBINATION_WITH_OTHER;
import static org.camunda.tngp.msgpack.mapping.MappingTestUtil.*;
import static org.camunda.tngp.msgpack.spec.MsgPackHelper.EMTPY_OBJECT;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

/**
 * Represents a test class to test the merge documents functionality with help of mappings.
 */
public class MappingMergeTest
{
    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private MappingProcessor processor = new MappingProcessor(1024);

    @Test
    public void shouldThrowExceptionIfSourceDocumentIsNull() throws Throwable
    {
        // given payload
        final Mapping mapping = createMapping("$", "$");

        // expect
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage("Target document must not be null!");

        // when
        processor.merge(null, null, mapping);
    }

    @Test
    public void shouldThrowExceptionIfTargetDocumentIsNull() throws Throwable
    {
        // given payload
        final DirectBuffer targetDocument = new UnsafeBuffer(EMTPY_OBJECT);
        final Mapping mapping = createMapping("$", "$");

        // expect
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage("Source document must not be null!");

        // when
        processor.merge(null, targetDocument, mapping);
    }

    @Test
    public void shouldThrowExceptionIfMappingIsEmpty() throws Throwable
    {
        // given payload
        final DirectBuffer sourceDocument = new UnsafeBuffer(EMTPY_OBJECT);
        final Mapping[] mapping = createMappings().build();

        // expect
        expectedException.expect(MappingException.class);
        expectedException.expectMessage("Mapping must not be neither null nor empty!");

        // when
        processor.merge(sourceDocument, sourceDocument, mapping);
    }

    @Test
    public void shouldThrowExceptionIfMappingIsNull() throws Throwable
    {
        // given payload
        final DirectBuffer sourceDocument = new UnsafeBuffer(EMTPY_OBJECT);

        // expect
        expectedException.expect(MappingException.class);
        expectedException.expectMessage("Mapping must not be neither null nor empty!");

        // when
        processor.merge(sourceDocument, sourceDocument, null);
    }

    @Test
    public void shouldThrowExceptionIfMappingDoesNotMatch() throws Throwable
    {
        // given payload
        final DirectBuffer sourceDocument = new UnsafeBuffer(EMTPY_OBJECT);
        final Mapping mapping = createMapping("$.foo", "$");

        // expect
        expectedException.expect(MappingException.class);
        expectedException.expectMessage("No data found for query $.foo.");

        // when
        processor.merge(sourceDocument, sourceDocument, mapping);
    }

    @Test
    public void shouldExtractNothingFromEmptyObject() throws Throwable
    {
        // given payload
        final DirectBuffer sourceDocument = new UnsafeBuffer(EMTPY_OBJECT);
        final Mapping mapping = createMapping("$", "$");

        // when
        final int resultLength = processor.merge(sourceDocument, sourceDocument, mapping);

        final MutableDirectBuffer resultBuffer = processor.getResultBuffer();
        final byte result[] = new byte[resultLength];
        resultBuffer.getBytes(0, result, 0, resultLength);

        // then result is expected as
        assertThat(result).isEqualTo(EMTPY_OBJECT);
    }

    @Test
    public void shouldMergeTwoSameDocuments() throws Throwable
    {
        // given payload
        final DirectBuffer sourceDocument = new UnsafeBuffer(MSG_PACK_BYTES);
        final Mapping[] mapping =
                createMappings()
                        .mapping("$", "$")
                        .build();

        // when
        final int resultLength = processor.merge(sourceDocument, sourceDocument, mapping);

        final MutableDirectBuffer resultBuffer = processor.getResultBuffer();
        final byte result[] = new byte[resultLength];
        resultBuffer.getBytes(0, result, 0, resultLength);

        // then result is expected as
        assertThat(MSG_PACK_BYTES).isEqualTo(MSG_PACK_BYTES);
    }

    @Test
    public void shouldMergeAndAddValueToTargetDocument() throws Throwable
    {
        // given payload
        final DirectBuffer sourceDocument = new UnsafeBuffer(MSG_PACK_BYTES);
        final Mapping[] mapping =
                createMappings()
                        .mapping(NODE_STRING_PATH, "$.newFoo")
                        .build();

        // when
        final int resultLength = processor.merge(sourceDocument, sourceDocument, mapping);

        final MutableDirectBuffer resultBuffer = processor.getResultBuffer();
        final byte result[] = new byte[resultLength];
        resultBuffer.getBytes(0, result, 0, resultLength);

        // then result is expected as
        final JsonNode jsonNode = OBJECT_MAPPER.readTree(result);
        assertThatNodeContainsTheStartingPayload(jsonNode);

        // and the new one
        final JsonNode newFooNode = jsonNode.get("newFoo");
        assertThat(newFooNode.textValue()).isEqualTo(NODE_STRING_VALUE);
    }

    @Test
    public void shouldMergeAndAddObjectToTargetDocument() throws Throwable
    {
        // given payload
        final DirectBuffer sourceDocument = new UnsafeBuffer(MSG_PACK_BYTES);
        final Mapping[] mapping =
                createMappings()
                        .mapping(NODE_JSON_OBJECT_PATH, "$.newObj")
                        .build();

        // when
        final int resultLength = processor.merge(sourceDocument, sourceDocument, mapping);

        final MutableDirectBuffer resultBuffer = processor.getResultBuffer();
        final byte result[] = new byte[resultLength];
        resultBuffer.getBytes(0, result, 0, resultLength);

        // then result is expected as
        final JsonNode jsonNode = OBJECT_MAPPER.readTree(result);
        assertThatNodeContainsTheStartingPayload(jsonNode);

        // and the new one
        final JsonNode objNode = jsonNode.get("newObj");
        assertThat(objNode.isObject()).isTrue();
        assertThat(objNode.get(NODE_TEST_ATTR_KEY).textValue()).isEqualTo(NODE_TEST_ATTR_VALUE);
    }

    @Test
    public void shouldMergeAndAddArrayToTargetDocument() throws Throwable
    {
        // given payload
        final DirectBuffer sourceDocument = new UnsafeBuffer(MSG_PACK_BYTES);
        final Mapping[] mapping =
                createMappings()
                        .mapping(NODE_ARRAY_PATH, "$.newArray")
                        .build();

        // when
        final int resultLength = processor.merge(sourceDocument, sourceDocument, mapping);

        final MutableDirectBuffer resultBuffer = processor.getResultBuffer();
        final byte result[] = new byte[resultLength];
        resultBuffer.getBytes(0, result, 0, resultLength);

        // then result is expected as
        final JsonNode jsonNode = OBJECT_MAPPER.readTree(result);
        assertThatNodeContainsTheStartingPayload(jsonNode);

        // and the new one
        final JsonNode newArrayNode = jsonNode.get("newArray");
        assertThatNodeContainsStartingArray(newArrayNode);
    }

    @Test
    public void shouldMergeAndExtractIndexObjectAndAddToTargetDocument() throws Throwable
    {
        // given payload
        final Map<String, Object> jsonObject = new HashMap<>();
        jsonObject.put(NODE_TEST_ATTR_KEY, NODE_TEST_ATTR_VALUE);
        final Object[] array = {jsonObject};

        final Map<String, Object> rootObj = new HashMap<>();
        rootObj.put(NODE_ARRAY_KEY, array);
        final byte[] bytes = OBJECT_MAPPER.writeValueAsBytes(rootObj);
        final DirectBuffer sourceDocument = new UnsafeBuffer(bytes);
        final Mapping[] mapping =
                createMappings()
                        .mapping(NODE_ARRAY_FIRST_IDX_PATH + "." + NODE_TEST_ATTR_KEY, "$.testValue")
                        .build();

        // when
        final int resultLength = processor.merge(sourceDocument, sourceDocument, mapping);

        final MutableDirectBuffer resultBuffer = processor.getResultBuffer();
        final byte result[] = new byte[resultLength];
        resultBuffer.getBytes(0, result, 0, resultLength);

        // then result is expected as
        final JsonNode jsonNode = OBJECT_MAPPER.readTree(result);
        final JsonNode arrayNode = jsonNode.get(NODE_ARRAY_KEY);
        assertThat(arrayNode.isArray()).isTrue();

        final JsonNode arrayObjectNode = arrayNode.get(0);
        assertThat(arrayObjectNode.isObject()).isTrue();
        assertThat(arrayObjectNode.get(NODE_TEST_ATTR_KEY).textValue()).isEqualTo(NODE_TEST_ATTR_VALUE);

        // and the new one
        final JsonNode testValueNode = jsonNode.get("testValue");
        assertThat(testValueNode.isTextual()).isTrue();
        assertThat(testValueNode.textValue()).isEqualTo(NODE_TEST_ATTR_VALUE);
    }

    @Test
    public void shouldMergeAndExtractIndexValueAndAddToTargetDocument() throws Throwable
    {
        // given payload
        final DirectBuffer sourceDocument = new UnsafeBuffer(MSG_PACK_BYTES);
        final Mapping[] mapping =
                createMappings()
                        .mapping(NODE_ARRAY_FIRST_IDX_PATH, "$.newValue")
                        .build();

        // when
        final int resultLength = processor.merge(sourceDocument, sourceDocument, mapping);

        final MutableDirectBuffer resultBuffer = processor.getResultBuffer();
        final byte result[] = new byte[resultLength];
        resultBuffer.getBytes(0, result, 0, resultLength);

        // then result is expected as
        final JsonNode jsonNode = OBJECT_MAPPER.readTree(result);
        assertThatNodeContainsTheStartingPayload(jsonNode);

        // and the new one
        final JsonNode newValueNode = jsonNode.get("newValue");
        assertThat(newValueNode.isInt()).isTrue();
        assertThat(newValueNode.intValue()).isEqualTo(0);
    }

    @Test
    public void shouldMergeAndReplaceValueAtIndex() throws Throwable
    {
        // given payload
        final DirectBuffer sourceDocument = new UnsafeBuffer(MSG_PACK_BYTES);
        final Mapping[] mapping =
                createMappings()
                        .mapping(NODE_STRING_PATH, NODE_ARRAY_FIRST_IDX_PATH)
                        .build();

        // when
        final int resultLength = processor.merge(sourceDocument, sourceDocument, mapping);

        final MutableDirectBuffer resultBuffer = processor.getResultBuffer();
        final byte result[] = new byte[resultLength];
        resultBuffer.getBytes(0, result, 0, resultLength);

        // then result is expected as
        final JsonNode jsonNode = OBJECT_MAPPER.readTree(result);
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

        // and overridden value on index 0
        final JsonNode arrayNode = jsonNode.get(NODE_ARRAY_KEY);
        assertThat(arrayNode.isArray()).isTrue();
        assertThat(arrayNode.get(0).textValue()).isEqualTo(NODE_STRING_VALUE);
        assertThat(arrayNode.get(1).intValue()).isEqualTo(1);
        assertThat(arrayNode.get(2).intValue()).isEqualTo(2);
        assertThat(arrayNode.get(3).intValue()).isEqualTo(3);
    }

    @Test
    public void shouldMergeAndReplaceObjectAtIndex() throws Throwable
    {
        // given payload
        final Map<String, Object> jsonObject = new HashMap<>();
        jsonObject.put(NODE_TEST_ATTR_KEY, NODE_TEST_ATTR_VALUE);
        final Object[] array = {jsonObject};

        final Map<String, Object> rootObj = new HashMap<>();
        rootObj.put(NODE_ARRAY_KEY, array);
        rootObj.put(NODE_STRING_KEY, NODE_STRING_VALUE);
        final byte[] bytes = OBJECT_MAPPER.writeValueAsBytes(rootObj);
        final DirectBuffer sourceDocument = new UnsafeBuffer(bytes);
        final Mapping[] mapping =
                createMappings()
                        .mapping(NODE_STRING_PATH, NODE_ARRAY_FIRST_IDX_PATH + "." + NODE_TEST_ATTR_KEY)
                        .build();

        // when
        final int resultLength = processor.merge(sourceDocument, sourceDocument, mapping);

        final MutableDirectBuffer resultBuffer = processor.getResultBuffer();
        final byte result[] = new byte[resultLength];
        resultBuffer.getBytes(0, result, 0, resultLength);

        // then result is expected as
        final JsonNode jsonNode = OBJECT_MAPPER.readTree(result);

        final JsonNode testValueNode = jsonNode.get(NODE_STRING_KEY);
        assertThat(testValueNode.isTextual()).isTrue();
        assertThat(testValueNode.textValue()).isEqualTo(NODE_STRING_VALUE);

        // and the new one
        final JsonNode arrayNode = jsonNode.get(NODE_ARRAY_KEY);
        assertThat(arrayNode.isArray()).isTrue();

        final JsonNode arrayObjectNode = arrayNode.get(0);
        assertThat(arrayObjectNode.isObject()).isTrue();
        assertThat(arrayObjectNode.get(NODE_TEST_ATTR_KEY).textValue()).isEqualTo(NODE_STRING_VALUE);
    }

    @Test
    public void shouldMergeAndAddObjectsToTargetDocument() throws Throwable
    {
        // given payload
        final DirectBuffer sourceDocument = new UnsafeBuffer(MSG_PACK_BYTES);
        final Mapping[] mapping =
                createMappings()
                .mapping(NODE_STRING_PATH, "$.newFoo")
                .mapping(NODE_JSON_OBJECT_PATH, "$.newObj")
                .build();

        // when
        final int resultLength = processor.merge(sourceDocument, sourceDocument, mapping);

        final MutableDirectBuffer resultBuffer = processor.getResultBuffer();
        final byte result[] = new byte[resultLength];
        resultBuffer.getBytes(0, result, 0, resultLength);

        // then result is expected as
        final JsonNode jsonNode = OBJECT_MAPPER.readTree(result);

        assertThatNodeContainsTheStartingPayload(jsonNode);

        // and the new ones
        final JsonNode newFooNode = jsonNode.get("newFoo");
        assertThat(newFooNode.textValue()).isEqualTo(NODE_STRING_VALUE);

        final JsonNode objNode = jsonNode.get("newObj");
        assertThat(objNode.isObject()).isTrue();
        assertThat(objNode.get(NODE_TEST_ATTR_KEY).textValue()).isEqualTo(NODE_TEST_ATTR_VALUE);
    }

    @Test
    public void shouldMergeAndReplaceObjectWithValue() throws Throwable
    {
        // given payload
        final DirectBuffer sourceDocument = new UnsafeBuffer(MSG_PACK_BYTES);
        final Mapping[] mapping =
                createMappings()
                        .mapping(NODE_STRING_PATH, NODE_JSON_OBJECT_PATH)
                        .build();

        // when
        final int resultLength = processor.merge(sourceDocument, sourceDocument, mapping);

        final MutableDirectBuffer resultBuffer = processor.getResultBuffer();
        final byte result[] = new byte[resultLength];
        resultBuffer.getBytes(0, result, 0, resultLength);

        // then result is expected as
        final JsonNode jsonNode = OBJECT_MAPPER.readTree(result);
        assertThat(jsonNode).isNotNull().isNotEmpty();

        final JsonNode fooNode = jsonNode.get(NODE_STRING_KEY);
        assertThat(fooNode.textValue()).isEqualTo(NODE_STRING_VALUE);

        // and the replaced value
        final JsonNode objNode = jsonNode.get(NODE_JSON_OBJECT_KEY);
        assertThat(objNode.textValue()).isEqualTo(NODE_STRING_VALUE);
    }

    @Test
    public void shouldMergeAndReplaceValueWithObject() throws Throwable
    {
        // given payload
        final DirectBuffer sourceDocument = new UnsafeBuffer(MSG_PACK_BYTES);
        final Mapping[] mapping =
                createMappings()
                        .mapping(NODE_JSON_OBJECT_PATH, NODE_STRING_PATH)
                        .build();

        // when
        final int resultLength = processor.merge(sourceDocument, sourceDocument, mapping);

        final MutableDirectBuffer resultBuffer = processor.getResultBuffer();
        final byte result[] = new byte[resultLength];
        resultBuffer.getBytes(0, result, 0, resultLength);

        // then result is expected as
        final JsonNode jsonNode = OBJECT_MAPPER.readTree(result);
        assertThat(jsonNode).isNotNull().isNotEmpty();

        final JsonNode fooNode = jsonNode.get(NODE_STRING_KEY);
        assertThat(fooNode.isObject()).isTrue();
        assertThat(fooNode.get(NODE_TEST_ATTR_KEY).textValue()).isEqualTo(NODE_TEST_ATTR_VALUE);

        // and the replaced object
        final JsonNode objNode = jsonNode.get(NODE_JSON_OBJECT_KEY);
        assertThat(objNode.isObject()).isTrue();
        assertThat(objNode.get(NODE_TEST_ATTR_KEY).textValue()).isEqualTo(NODE_TEST_ATTR_VALUE);
    }

    @Test
    public void shouldMergeAndReplaceObjectWithComplexObject() throws Throwable
    {
        // given payload
        final Map<String, Object> jsonObject = new HashMap<>();
        jsonObject.put(NODE_TEST_ATTR_KEY, NODE_TEST_ATTR_VALUE);
        jsonObject.put("testObj", JSON_PAYLOAD);
        jsonObject.put("a", JSON_PAYLOAD);
        final DirectBuffer sourceDocument = new UnsafeBuffer(OBJECT_MAPPER.writeValueAsBytes(jsonObject));
        final Mapping[] mapping =
                createMappings()
                        .mapping(NODE_ROOT_PATH, NODE_JSON_OBJECT_PATH)
                        .build();

        // when
        final int resultLength = processor.merge(sourceDocument, sourceDocument, mapping);

        final MutableDirectBuffer resultBuffer = processor.getResultBuffer();
        final byte result[] = new byte[resultLength];
        resultBuffer.getBytes(0, result, 0, resultLength);

        // then result is expected as
        final JsonNode jsonNode = OBJECT_MAPPER.readTree(result);
        assertThat(jsonNode).isNotNull().isNotEmpty();

        final JsonNode stringNode = jsonNode.get(NODE_TEST_ATTR_KEY);
        assertThat(stringNode.isTextual()).isTrue();
        assertThat(stringNode.textValue()).isEqualTo(NODE_TEST_ATTR_VALUE);

        JsonNode aNode = jsonNode.get("a");
        assertThatNodeContainsTheStartingPayload(aNode);

        JsonNode testObjNode = jsonNode.get("testObj");
        assertThatNodeContainsTheStartingPayload(testObjNode);

        // and the replaced object
        final JsonNode objNode = jsonNode.get(NODE_JSON_OBJECT_KEY);
        assertThat(objNode.isObject()).isTrue();
        assertThat(objNode.get(NODE_TEST_ATTR_KEY).textValue()).isEqualTo(NODE_TEST_ATTR_VALUE);

        aNode = objNode.get("a");
        assertThatNodeContainsTheStartingPayload(aNode);

        testObjNode = objNode.get("testObj");
        assertThatNodeContainsTheStartingPayload(testObjNode);
    }

    @Test
    public void shouldMergeAndReplaceObjectWithSameNameInDepth() throws Throwable
    {
        // given payload
        final Map<String, Object> jsonObject = new HashMap<>();
        jsonObject.put("value", NODE_TEST_ATTR_VALUE);
        jsonObject.put("complexObject1", JSON_PAYLOAD);
        jsonObject.put("complexObject2", JSON_PAYLOAD);
        final DirectBuffer sourceDocument = new UnsafeBuffer(OBJECT_MAPPER.writeValueAsBytes(jsonObject));
        final Mapping[] mapping =
                createMappings()
                        .mapping("$.value", "$.complexObject2.jsonObject")
                        .build();

        // when
        final int resultLength = processor.merge(sourceDocument, sourceDocument, mapping);

        final MutableDirectBuffer resultBuffer = processor.getResultBuffer();
        final byte result[] = new byte[resultLength];
        resultBuffer.getBytes(0, result, 0, resultLength);

        // then result is expected as
        final JsonNode jsonNode = OBJECT_MAPPER.readTree(result);
        assertThat(jsonNode).isNotNull().isNotEmpty();

        final JsonNode valueNode = jsonNode.get("value");
        assertThat(valueNode.isTextual()).isTrue();
        assertThat(valueNode.textValue()).isEqualTo(NODE_TEST_ATTR_VALUE);

        // first complex obj is the same like before
        final JsonNode complexObject1Node = jsonNode.get("complexObject1");
        assertThatNodeContainsTheStartingPayload(complexObject1Node);

        // and the replaced object
        final JsonNode complexObject2Node = jsonNode.get("complexObject2");
        assertThat(complexObject2Node.isObject()).isTrue();

        // contains old content
        final JsonNode fooNode = complexObject2Node.get(NODE_STRING_KEY);
        assertThat(fooNode.textValue()).isEqualTo(NODE_STRING_VALUE);

        final JsonNode booleanNode = complexObject2Node.get(NODE_BOOLEAN_KEY);
        assertThat(booleanNode.booleanValue()).isEqualTo(NODE_BOOLEAN_VALUE);

        final JsonNode integerNode = complexObject2Node.get(NODE_INTEGER_KEY);
        assertThat(integerNode.intValue()).isEqualTo(NODE_INTEGER_VALUE);

        final JsonNode longNode = complexObject2Node.get(NODE_LONG_KEY);
        assertThat(longNode.longValue()).isEqualTo(NODE_LONG_VALUE);

        final JsonNode doubleNode = complexObject2Node.get(NODE_DOUBLE_KEY);
        assertThat(doubleNode.doubleValue()).isEqualTo(NODE_DOUBLE_VALUE);

        final JsonNode arrayNode = complexObject2Node.get(NODE_ARRAY_KEY);
        assertThatNodeContainsStartingArray(arrayNode);

        // and json object is replaced with value
        final JsonNode jsonObjNode = complexObject2Node.get(NODE_JSON_OBJECT_KEY);
        assertThat(jsonObjNode.isTextual()).isTrue();
        assertThat(jsonObjNode.textValue()).isEqualTo(NODE_TEST_ATTR_VALUE);
    }

    @Test
    public void shouldMergeAndAddObjectInsideOfExistingObject() throws Throwable
    {
        // given payload
        final DirectBuffer sourceDocument = new UnsafeBuffer(MSG_PACK_BYTES);
        final Mapping[] mapping =
                createMappings()
                        .mapping(NODE_STRING_PATH, "$.jsonObject.newFoo")
                        .build();

        // when
        final int resultLength = processor.merge(sourceDocument, sourceDocument, mapping);

        final MutableDirectBuffer resultBuffer = processor.getResultBuffer();
        final byte result[] = new byte[resultLength];
        resultBuffer.getBytes(0, result, 0, resultLength);

        // then result is expected as
        final JsonNode jsonNode = OBJECT_MAPPER.readTree(result);
        assertThatNodeContainsTheStartingPayload(jsonNode);

        // and the new added value in the existing object
        assertThat(jsonNode.get(NODE_JSON_OBJECT_KEY).get("newFoo").textValue()).isEqualTo(NODE_STRING_VALUE);
    }

    @Test
    public void shouldMergeAndAddHoleDocumentToNewObject() throws Throwable
    {
        // given payload
        final DirectBuffer sourceDocument = new UnsafeBuffer(MSG_PACK_BYTES);
        final Mapping[] mapping =
                createMappings()
                        .mapping(NODE_ROOT_PATH, "$.taskPayload")
                        .build();

        // when
        final int resultLength = processor.merge(sourceDocument, sourceDocument, mapping);

        final MutableDirectBuffer resultBuffer = processor.getResultBuffer();
        final byte result[] = new byte[resultLength];
        resultBuffer.getBytes(0, result, 0, resultLength);

        // then result is expected as
        final JsonNode jsonNode = OBJECT_MAPPER.readTree(result);
        assertThatNodeContainsTheStartingPayload(jsonNode);

        // and the new added task payload
        final JsonNode taskPayloadNode = jsonNode.get("taskPayload");
        assertThatNodeContainsTheStartingPayload(taskPayloadNode);
    }

    @Test
    public void shouldMergeAndReplaceDocumentWithObject() throws Throwable
    {
        // given payload
        final DirectBuffer sourceDocument = new UnsafeBuffer(MSG_PACK_BYTES);
        final Mapping[] mapping =
                createMappings()
                        .mapping(NODE_JSON_OBJECT_PATH, NODE_ROOT_PATH)
                        .build();

        // when
        final int resultLength = processor.merge(sourceDocument, sourceDocument, mapping);

        final MutableDirectBuffer resultBuffer = processor.getResultBuffer();
        final byte result[] = new byte[resultLength];
        resultBuffer.getBytes(0, result, 0, resultLength);

        // then result is expected as
        final JsonNode jsonNode = OBJECT_MAPPER.readTree(result);
        assertThat(jsonNode.isObject()).isTrue();
        assertThat(jsonNode.get(NODE_TEST_ATTR_KEY).textValue()).isEqualTo(NODE_TEST_ATTR_VALUE);
    }


    @Test
    public void shouldMergeAndReplaceHoleDocumentWithValue() throws Throwable
    {
        // given payload
        final DirectBuffer sourceDocument = new UnsafeBuffer(MSG_PACK_BYTES);
        final Mapping[] mapping =
                createMappings()
                        .mapping(NODE_STRING_PATH, NODE_ROOT_PATH)
                        .build();

        // when
        final int resultLength = processor.merge(sourceDocument, sourceDocument, mapping);

        final MutableDirectBuffer resultBuffer = processor.getResultBuffer();
        final byte result[] = new byte[resultLength];
        resultBuffer.getBytes(0, result, 0, resultLength);

        // then result is expected as
        final JsonNode jsonNode = OBJECT_MAPPER.readTree(result);
        assertThat(jsonNode.isTextual()).isTrue();
        assertThat(jsonNode.textValue()).isEqualTo(NODE_STRING_VALUE);
    }

    @Test
    public void shouldMergeAndOverwriteObjectForDuplicateMapping() throws Throwable
    {
        // given payload
        final DirectBuffer sourceDocument = new UnsafeBuffer(MSG_PACK_BYTES);
        final Mapping[] mapping =
                createMappings()
                        .mapping(NODE_STRING_PATH, "$.newObj")
                        .mapping(NODE_JSON_OBJECT_PATH, "$.newObj")
                        .build();

        // when
        final int resultLength = processor.merge(sourceDocument, sourceDocument, mapping);

        final MutableDirectBuffer resultBuffer = processor.getResultBuffer();
        final byte result[] = new byte[resultLength];
        resultBuffer.getBytes(0, result, 0, resultLength);

        // then result is expected as
        final JsonNode jsonNode = OBJECT_MAPPER.readTree(result);
        assertThatNodeContainsTheStartingPayload(jsonNode);

        // new obj has value of json object
        final JsonNode newObjNode = jsonNode.get("newObj");
        assertThat(newObjNode.isObject()).isTrue();
        assertThat(newObjNode.get(NODE_TEST_ATTR_KEY).textValue()).isEqualTo(NODE_TEST_ATTR_VALUE);
    }

    @Test
    public void shouldThrowExceptionIfRootMappingWithOtherMappingIsUsed() throws Throwable
    {
        // given payload
        final DirectBuffer sourceDocument = new UnsafeBuffer(MSG_PACK_BYTES);
        final Mapping[] mapping =
                createMappings()
                    .mapping(NODE_JSON_OBJECT_PATH, NODE_JSON_OBJECT_PATH)
                    .mapping(NODE_STRING_PATH, NODE_ROOT_PATH)
                    .build();

        // expect
        expectedException.expect(MappingException.class);
        expectedException.expectMessage(EXCEPTION_MSG_ROOT_MAPPING_IN_COMBINATION_WITH_OTHER);

        // when
        processor.merge(sourceDocument, sourceDocument, mapping);
    }

    @Test
    public void shouldMergeAndExtract() throws Throwable
    {
        // given payload
        final DirectBuffer sourceDocument = new UnsafeBuffer(MSG_PACK_BYTES);
        final Mapping[] extractMapping = createMappings().mapping(NODE_JSON_OBJECT_PATH, NODE_ROOT_PATH).build();
        final Mapping[] mergeMapping = createMappings().mapping("$.testAttr", NODE_ROOT_PATH).build();

        // when
        int resultLength = processor.extract(sourceDocument, extractMapping);

        MutableDirectBuffer resultBuffer = processor.getResultBuffer();
        byte result[] = new byte[resultLength];
        resultBuffer.getBytes(0, result, 0, resultLength);

        // and merge
        resultLength = processor.merge(resultBuffer, sourceDocument, mergeMapping);

        resultBuffer = processor.getResultBuffer();
        result = new byte[resultLength];
        resultBuffer.getBytes(0, result, 0, resultLength);

        // then result is expected as
        final JsonNode jsonNode = OBJECT_MAPPER.readTree(result);
        assertThat(jsonNode).isNotNull();
        assertThat(jsonNode.isTextual()).isTrue();
        assertThat(jsonNode.textValue()).isEqualTo(NODE_TEST_ATTR_VALUE);
    }

}

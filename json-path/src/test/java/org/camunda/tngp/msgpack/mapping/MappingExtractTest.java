package org.camunda.tngp.msgpack.mapping;

import com.fasterxml.jackson.databind.JsonNode;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.tngp.msgpack.mapping.MappingProcessor.EXCEPTION_MSG_ROOT_MAPPING_IN_COMBINATION_WITH_OTHER;
import static org.camunda.tngp.msgpack.mapping.MappingTestUtil.*;
import static org.camunda.tngp.msgpack.mapping.MappingBuilder.createMapping;
import static org.camunda.tngp.msgpack.mapping.MappingBuilder.createMappings;
import static org.camunda.tngp.msgpack.spec.MsgPackHelper.EMTPY_OBJECT;

/**
 * Represents a test class to test the extract document functionality with help of mappings.
 */
public class MappingExtractTest
{
    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private MappingProcessor processor = new MappingProcessor(1024);

    @Test
    public void shouldThrowExceptionIfDocumentIsNull() throws Throwable
    {
        // given payload
        final Mapping mapping = createMapping("$", "$");

        // expect
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage("Source document must not be null!");

        // when
        processor.extract(null, mapping);
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
        processor.extract(sourceDocument, mapping);
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
        processor.extract(sourceDocument, null);
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
        processor.extract(sourceDocument, mapping);
    }

    @Test
    public void shouldThrowExceptionIfRootMappingWithOtherMappingIsUsed() throws Throwable
    {
        // given payload
        final DirectBuffer sourceDocument = new UnsafeBuffer(MSG_PACK_BYTES);
        final Mapping[] mapping = createMappings().mapping(NODE_JSON_OBJECT_PATH, NODE_JSON_OBJECT_PATH)
                                                  .mapping(NODE_STRING_PATH, NODE_ROOT_PATH)
                                                  .build();

        // expect
        expectedException.expect(MappingException.class);
        expectedException.expectMessage(EXCEPTION_MSG_ROOT_MAPPING_IN_COMBINATION_WITH_OTHER);

        // when
        processor.extract(sourceDocument, mapping);
    }

    @Test
    public void shouldExtractNothingFromEmptyObject() throws Throwable
    {
        // given payload
        final DirectBuffer sourceDocument = new UnsafeBuffer(EMTPY_OBJECT);
        final Mapping mapping = createMapping("$", "$");

        // when
        final int resultLength = processor.extract(sourceDocument, mapping);

        final MutableDirectBuffer resultBuffer = processor.getResultBuffer();
        final byte result[] = new byte[resultLength];
        resultBuffer.getBytes(0, result, 0, resultLength);

        // then result is expected as
        assertThat(result).isEqualTo(EMTPY_OBJECT);
    }

    @Test
    public void shouldExtractHoleDocument() throws Throwable
    {
        // given payload
        final DirectBuffer sourceDocument = new UnsafeBuffer(MSG_PACK_BYTES);
        final Mapping mapping = createMapping("$", "$");

        // when
        final int resultLength = processor.extract(sourceDocument, mapping);

        final MutableDirectBuffer resultBuffer = processor.getResultBuffer();
        final byte result[] = new byte[resultLength];
        resultBuffer.getBytes(0, result, 0, resultLength);

        // then
        assertThat(MSG_PACK_BYTES).isEqualTo(result);
    }

    @Test
    public void shouldExtractValueFromDocumentAndWriteIntoTarget() throws Throwable
    {
        // given payload
        final DirectBuffer sourceDocument = new UnsafeBuffer(MSG_PACK_BYTES);
        final Mapping mapping = createMapping(NODE_STRING_PATH, "$.newFoo");

        // when
        final int resultLength = processor.extract(sourceDocument, mapping);

        final MutableDirectBuffer resultBuffer = processor.getResultBuffer();
        final byte result[] = new byte[resultLength];
        resultBuffer.getBytes(0, result, 0, resultLength);

        // then result is expected as
        final JsonNode jsonNode = OBJECT_MAPPER.readTree(result);
        assertThat(jsonNode).isNotNull()
                            .isNotEmpty();

        final JsonNode newFooNode = jsonNode.get("newFoo");
        assertThat(newFooNode.textValue()).isEqualTo(NODE_STRING_VALUE);
    }

    @Test
    public void shouldExtractValueFromDocumentAndWriteIntoDeepTarget() throws Throwable
    {
        // given payload
        final DirectBuffer sourceDocument = new UnsafeBuffer(MSG_PACK_BYTES);
        final Mapping mapping = createMapping(NODE_STRING_PATH, "$.newFoo.newDepth.string");

        // when
        final int resultLength = processor.extract(sourceDocument, mapping);

        final MutableDirectBuffer resultBuffer = processor.getResultBuffer();
        final byte result[] = new byte[resultLength];
        resultBuffer.getBytes(0, result, 0, resultLength);

        // then result is expected as
        final JsonNode jsonNode = OBJECT_MAPPER.readTree(result);
        assertThat(jsonNode).isNotNull()
                            .isNotEmpty();

        final JsonNode newFooNode = jsonNode.get("newFoo")
                                            .get("newDepth")
                                            .get(NODE_STRING_KEY);
        assertThat(newFooNode.textValue()).isEqualTo(NODE_STRING_VALUE);
    }

    @Test
    public void shouldExtractObjectFromDocumentAndWriteIntoTargetObject() throws Throwable
    {
        // given payload
        final DirectBuffer sourceDocument = new UnsafeBuffer(MSG_PACK_BYTES);
        final Mapping mapping = createMapping(NODE_JSON_OBJECT_PATH, "$.newObj");

        // when
        final int resultLength = processor.extract(sourceDocument, mapping);

        final MutableDirectBuffer resultBuffer = processor.getResultBuffer();
        final byte result[] = new byte[resultLength];
        resultBuffer.getBytes(0, result, 0, resultLength);

        // then result is expected as
        final JsonNode jsonNode = OBJECT_MAPPER.readTree(result);
        assertThat(jsonNode).isNotNull()
                            .isNotEmpty();

        final JsonNode newObjNode = jsonNode.get("newObj");
        assertThat(newObjNode.isObject()).isTrue();
        assertThat(newObjNode.get(NODE_TEST_ATTR_KEY)
                             .textValue()).isEqualTo(NODE_TEST_ATTR_VALUE);
    }

    @Test
    public void shouldExtractArrayFromDocumentAndWriteIntoTargetArray() throws Throwable
    {
        // given payload
        final DirectBuffer sourceDocument = new UnsafeBuffer(MSG_PACK_BYTES);
        final Mapping mapping = createMapping(NODE_ARRAY_PATH, "$.newArray");

        // when
        final int resultLength = processor.extract(sourceDocument, mapping);

        final MutableDirectBuffer resultBuffer = processor.getResultBuffer();
        final byte result[] = new byte[resultLength];
        resultBuffer.getBytes(0, result, 0, resultLength);

        // then result is expected as
        final JsonNode jsonNode = OBJECT_MAPPER.readTree(result);
        assertThat(jsonNode).isNotNull()
                            .isNotEmpty();

        final JsonNode newArrayNode = jsonNode.get("newArray");
        assertThatNodeContainsStartingArray(newArrayNode);
    }

    @Test
    public void shouldExtractArrayIndexFromDocumentAndWriteIntoTarget() throws Throwable
    {
        // given payload
        final DirectBuffer sourceDocument = new UnsafeBuffer(MSG_PACK_BYTES);
        final Mapping mapping = createMapping(NODE_ARRAY_FIRST_IDX_PATH, "$.firstIdxValue");

        // when
        final int resultLength = processor.extract(sourceDocument, mapping);

        final MutableDirectBuffer resultBuffer = processor.getResultBuffer();
        final byte result[] = new byte[resultLength];
        resultBuffer.getBytes(0, result, 0, resultLength);

        // then result is expected as
        final JsonNode jsonNode = OBJECT_MAPPER.readTree(result);
        assertThat(jsonNode).isNotNull()
                            .isNotEmpty();

        final JsonNode newArrayNode = jsonNode.get("firstIdxValue");
        assertThat(newArrayNode.isInt()).isTrue();
        assertThat(newArrayNode.intValue()).isEqualTo(0);
    }

    @Test
    public void shouldExtractArrayIndexFromDocumentAndWriteIntoTargetIndex() throws Throwable
    {
        // given payload
        final DirectBuffer sourceDocument = new UnsafeBuffer(MSG_PACK_BYTES);
        final Mapping mapping = MappingBuilder.createMapping("$.array[1]", "$.array[0]");

        // when
        final int resultLength = processor.extract(sourceDocument, mapping);

        final MutableDirectBuffer resultBuffer = processor.getResultBuffer();
        final byte result[] = new byte[resultLength];
        resultBuffer.getBytes(0, result, 0, resultLength);

        // then result is expected as
        final JsonNode jsonNode = OBJECT_MAPPER.readTree(result);
        assertThat(jsonNode).isNotNull()
                            .isNotEmpty();

        final JsonNode newArrayNode = jsonNode.get("array").get(0);
        assertThat(newArrayNode.isInt()).isTrue();
        assertThat(newArrayNode.intValue()).isEqualTo(1);
    }

    @Test
    public void shouldExtractArrayIndexFromDocumentAndWriteIntoTargetIndexObject() throws Throwable
    {
        // given payload
        final DirectBuffer sourceDocument = new UnsafeBuffer(MSG_PACK_BYTES);
        final Mapping mapping = MappingBuilder.createMapping("$.array[1]", "$.array[0].test");

        // when
        final int resultLength = processor.extract(sourceDocument, mapping);

        final MutableDirectBuffer resultBuffer = processor.getResultBuffer();
        final byte result[] = new byte[resultLength];
        resultBuffer.getBytes(0, result, 0, resultLength);

        // then result is expected as
        final JsonNode jsonNode = OBJECT_MAPPER.readTree(result);
        assertThat(jsonNode).isNotNull()
                            .isNotEmpty();

        final JsonNode newArrayNode = jsonNode.get("array").get(0).get("test");
        assertThat(newArrayNode.isInt()).isTrue();
        assertThat(newArrayNode.intValue()).isEqualTo(1);
    }


    @Test
    public void shouldExtractValueFromArrayIndexAndWriteIntoTarget() throws Throwable
    {
        // given payload
        final Map<String, Object> jsonObject = new HashMap<>();
        jsonObject.put(NODE_TEST_ATTR_KEY, NODE_TEST_ATTR_VALUE);
        final Object[] array = { jsonObject };

        final Map<String, Object> rootObj = new HashMap<>();
        rootObj.put(NODE_ARRAY_KEY, array);
        final byte[] bytes = OBJECT_MAPPER.writeValueAsBytes(rootObj);
        final DirectBuffer sourceDocument = new UnsafeBuffer(bytes);
        final Mapping mapping = createMapping(NODE_ARRAY_FIRST_IDX_PATH + "." + NODE_TEST_ATTR_KEY, "$.testValue");

        // when
        final int resultLength = processor.extract(sourceDocument, mapping);

        final MutableDirectBuffer resultBuffer = processor.getResultBuffer();
        final byte result[] = new byte[resultLength];
        resultBuffer.getBytes(0, result, 0, resultLength);

        // then result is expected as
        final JsonNode jsonNode = OBJECT_MAPPER.readTree(result);

        final JsonNode testValueNode = jsonNode.get("testValue");
        assertThat(testValueNode.isTextual()).isTrue();
        assertThat(testValueNode.textValue()).isEqualTo(NODE_TEST_ATTR_VALUE);
    }

    @Test
    public void shouldExtractTwoObjectsFromDocumentAndWriteIntoTarget() throws Throwable
    {
        // given payload
        final DirectBuffer sourceDocument = new UnsafeBuffer(MSG_PACK_BYTES);
        final Mapping[] mapping = createMappings().mapping(NODE_STRING_PATH, "$.newFoo")
                                                  .mapping(NODE_JSON_OBJECT_PATH, "$.newObj")
                                                  .build();

        // when
        final int resultLength = processor.extract(sourceDocument, mapping);

        final MutableDirectBuffer resultBuffer = processor.getResultBuffer();
        final byte result[] = new byte[resultLength];
        resultBuffer.getBytes(0, result, 0, resultLength);

        // then result is expected as
        final JsonNode jsonNode = OBJECT_MAPPER.readTree(result);
        assertThat(jsonNode).isNotNull()
                            .isNotEmpty();

        final JsonNode newFooNode = jsonNode.get("newFoo");
        assertThat(newFooNode.textValue()).isEqualTo(NODE_STRING_VALUE);

        final JsonNode newObjNode = jsonNode.get("newObj");
        assertThat(newObjNode.isObject()).isTrue();
        assertThat(newObjNode.get(NODE_TEST_ATTR_KEY)
                             .textValue()).isEqualTo(NODE_TEST_ATTR_VALUE);
    }

    @Test
    public void shouldExtractTwoObjectsFromDocumentAndWriteIntoSameDepthTarget() throws Throwable
    {
        // given payload
        final DirectBuffer sourceDocument = new UnsafeBuffer(MSG_PACK_BYTES);
        final Mapping[] mapping = createMappings().mapping(NODE_STRING_PATH, "$.newDepth.newFoo")
                                                  .mapping(NODE_JSON_OBJECT_PATH, "$.newDepth.newObj")
                                                  .build();

        // when
        final int resultLength = processor.extract(sourceDocument, mapping);

        final MutableDirectBuffer resultBuffer = processor.getResultBuffer();
        final byte result[] = new byte[resultLength];
        resultBuffer.getBytes(0, result, 0, resultLength);

        // then result is expected as
        final JsonNode jsonNode = OBJECT_MAPPER.readTree(result);
        assertThat(jsonNode).isNotNull()
                            .isNotEmpty();

        final JsonNode newFooNode = jsonNode.get("newDepth")
                                            .get("newFoo");
        assertThat(newFooNode.textValue()).isEqualTo(NODE_STRING_VALUE);

        final JsonNode newObjNode = jsonNode.get("newDepth")
                                            .get("newObj");
        assertThat(newObjNode.isObject()).isTrue();
        assertThat(newObjNode.get(NODE_TEST_ATTR_KEY)
                             .textValue()).isEqualTo(NODE_TEST_ATTR_VALUE);
    }

    @Test
    public void shouldExtractTwoObjectsFromDocumentAndOverwriteSameTarget() throws Throwable
    {
        // given payload
        final DirectBuffer sourceDocument = new UnsafeBuffer(MSG_PACK_BYTES);
        final Mapping[] mapping = createMappings().mapping(NODE_STRING_PATH, "$.newObj")
                                                  .mapping(NODE_JSON_OBJECT_PATH, "$.newObj")
                                                  .build();

        // when
        final int resultLength = processor.extract(sourceDocument, mapping);

        final MutableDirectBuffer resultBuffer = processor.getResultBuffer();
        final byte result[] = new byte[resultLength];
        resultBuffer.getBytes(0, result, 0, resultLength);

        // then result is expected as
        final JsonNode jsonNode = OBJECT_MAPPER.readTree(result);
        assertThat(jsonNode).isNotNull()
                            .isNotEmpty();

        final JsonNode newObjNode = jsonNode.get("newObj");
        assertThat(newObjNode.isObject()).isTrue();
        assertThat(newObjNode.get(NODE_TEST_ATTR_KEY)
                             .textValue()).isEqualTo(NODE_TEST_ATTR_VALUE);
    }
}
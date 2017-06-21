package org.camunda.tngp.msgpack.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.tngp.msgpack.mapping.MappingBuilder.createMapping;
import static org.camunda.tngp.msgpack.mapping.MappingBuilder.createMappings;
import static org.camunda.tngp.msgpack.mapping.MappingTestUtil.JSON_MAPPER;
import static org.camunda.tngp.msgpack.mapping.MappingTestUtil.MSGPACK_MAPPER;
import static org.camunda.tngp.msgpack.spec.MsgPackHelper.EMTPY_OBJECT;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

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
        // given mapping
        final Mapping[] mapping = createMapping("$", "$");

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
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage("Mapping must be neither null nor empty!");

        // when
        processor.extract(sourceDocument, mapping);
    }

    @Test
    public void shouldThrowExceptionIfMappingIsNull() throws Throwable
    {
        // given payload
        final DirectBuffer sourceDocument = new UnsafeBuffer(EMTPY_OBJECT);

        // expect
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage("Mapping must be neither null nor empty!");

        // when
        processor.extract(sourceDocument);
    }

    @Test
    public void shouldThrowExceptionIfMappingDoesNotMatch() throws Throwable
    {
        // given payload
        final DirectBuffer sourceDocument = new UnsafeBuffer(EMTPY_OBJECT);
        final Mapping[] mapping = createMapping("$.foo", "$");

        // expect
        expectedException.expect(MappingException.class);
        expectedException.expectMessage("No data found for query $.foo.");

        // when
        processor.extract(sourceDocument, mapping);
    }

    @Test
    public void shouldThrowExceptionIfResultDocumentIsNoObject() throws Throwable
    {
        // given payload
        final DirectBuffer sourceDocument = new UnsafeBuffer(
            MSGPACK_MAPPER.writeValueAsBytes(JSON_MAPPER.readTree("{'foo':'bar'}")));
        final Mapping[] mapping = createMapping("$.foo", "$");

        // expect
        expectedException.expect(MappingException.class);
        expectedException.expectMessage("Processing failed, since mapping will result in a non map object (json object).");

        // when
        processor.extract(sourceDocument, mapping);
    }

    @Test
    public void shouldExtractTwice() throws Throwable
    {
        // given documents
        final DirectBuffer sourceDocument = new UnsafeBuffer(
            MSGPACK_MAPPER.writeValueAsBytes(
                JSON_MAPPER.readTree("{'arr':[{'deepObj':{'value':123}}, 1], 'obj':{'int':1}, 'test':'value'}")));
        Mapping[] extractMapping = createMappings().mapping("$.arr[0]", "$").build();

        // when merge
        int resultLength = processor.extract(sourceDocument, extractMapping);
        MutableDirectBuffer resultBuffer = processor.getResultBuffer();
        byte result[] = new byte[resultLength];
        resultBuffer.getBytes(0, result, 0, resultLength);

        // then expect result
        assertThat(MSGPACK_MAPPER.readTree(result))
            .isEqualTo(JSON_MAPPER.readTree("{'deepObj':{'value':123}}"));

        // new source and mappings
        sourceDocument.wrap(result);
        extractMapping = createMappings().mapping("$.deepObj", "$").build();

        // when again merge after that
        resultLength = processor.extract(sourceDocument, extractMapping);
        resultBuffer = processor.getResultBuffer();
        result = new byte[resultLength];
        resultBuffer.getBytes(0, result, 0, resultLength);

        // then expect result
        assertThat(MSGPACK_MAPPER.readTree(result))
            .isEqualTo(JSON_MAPPER.readTree("{'value':123}"));
    }
}
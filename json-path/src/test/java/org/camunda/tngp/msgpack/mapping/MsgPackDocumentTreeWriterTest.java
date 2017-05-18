package org.camunda.tngp.msgpack.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.tngp.msgpack.mapping.MappingTestUtil.*;

import com.fasterxml.jackson.databind.JsonNode;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

public class MsgPackDocumentTreeWriterTest
{
    private MsgPackDocumentIndexer indexer = new MsgPackDocumentIndexer();
    private MsgPackDocumentTreeWriter writer = new MsgPackDocumentTreeWriter(256);

    @Test
    public void shouldWriteMsgPackTree() throws Exception
    {
        // given
        final MutableDirectBuffer buffer = new UnsafeBuffer(MSG_PACK_BYTES);
        indexer.wrap(buffer);
        final MsgPackTree documentTree = indexer.index();

        // when
        writer.wrap(documentTree);
        writer.write();

        // then
        final MutableDirectBuffer result = writer.getResult();
        final JsonNode jsonNode = OBJECT_MAPPER.readTree(result.byteArray());
        assertThatNodeContainsTheStartingPayload(jsonNode);
    }

    @Test
    public void shouldWriteMsgPackTreeWhenWriterHasSmallInitSize() throws Exception
    {
        // given
        final MsgPackDocumentTreeWriter writer = new MsgPackDocumentTreeWriter(64);
        final MutableDirectBuffer buffer = new UnsafeBuffer(MSG_PACK_BYTES);
        indexer.wrap(buffer);
        final MsgPackTree documentTree = indexer.index();

        // when
        writer.wrap(documentTree);
        final int resultLen = writer.write();

        // then
        assertThat(resultLen).isGreaterThan(64);
        final MutableDirectBuffer result = writer.getResult();
        assertThat(result.capacity()).isGreaterThan(64);
        final JsonNode jsonNode = OBJECT_MAPPER.readTree(result.byteArray());
        assertThatNodeContainsTheStartingPayload(jsonNode);
    }
}

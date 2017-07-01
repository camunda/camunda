package io.zeebe.msgpack.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static io.zeebe.msgpack.mapping.MappingTestUtil.jsonDocumentPath;
import static io.zeebe.msgpack.mapping.MappingTestUtil.MSGPACK_MAPPER;

import java.nio.file.Files;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
        final JsonNode jsonDocument = new ObjectMapper().readTree(Files.readAllBytes(jsonDocumentPath));
        final byte[] msgpackBytes = MSGPACK_MAPPER.writeValueAsBytes(jsonDocument);
        final MutableDirectBuffer buffer = new UnsafeBuffer(msgpackBytes);
        indexer.wrap(buffer);
        final MsgPackTree documentTree = indexer.index();

        // when
        final int resultLength = writer.write(documentTree);

        // then
        assertThat(resultLength).isEqualTo(msgpackBytes.length);
        final MutableDirectBuffer result = writer.getResult();
        assertThat(MSGPACK_MAPPER.readTree(result.byteArray())).isEqualTo(jsonDocument);
    }

    @Test
    public void shouldWriteMsgPackTreeWhenWriterHasSmallInitSize() throws Exception
    {
        // given
        final MsgPackDocumentTreeWriter writer = new MsgPackDocumentTreeWriter(64);
        final JsonNode jsonDocument = new ObjectMapper().readTree(Files.readAllBytes(jsonDocumentPath));
        final byte[] msgpackBytes = MSGPACK_MAPPER.writeValueAsBytes(jsonDocument);
        assertThat(msgpackBytes.length).isGreaterThan(64);
        final MutableDirectBuffer buffer = new UnsafeBuffer(msgpackBytes);
        indexer.wrap(buffer);
        final MsgPackTree documentTree = indexer.index();

        // when
        final int resultLength = writer.write(documentTree);

        // then
        assertThat(resultLength).isEqualTo(msgpackBytes.length);
        final MutableDirectBuffer result = writer.getResult();
        assertThat(result.capacity()).isGreaterThanOrEqualTo(msgpackBytes.length);
        assertThat(MSGPACK_MAPPER.readTree(result.byteArray())).isEqualTo(jsonDocument);
    }
}

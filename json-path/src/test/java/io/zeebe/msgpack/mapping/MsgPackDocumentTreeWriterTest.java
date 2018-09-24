/*
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

import static io.zeebe.msgpack.mapping.MappingTestUtil.MSGPACK_MAPPER;
import static io.zeebe.msgpack.mapping.MappingTestUtil.jsonDocumentPath;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

public class MsgPackDocumentTreeWriterTest {
  private MsgPackDocumentIndexer indexer = new MsgPackDocumentIndexer();
  private MsgPackDocumentTreeWriter writer = new MsgPackDocumentTreeWriter(256);

  @Test
  public void shouldWriteMsgPackTree() throws Exception {
    // given
    final JsonNode jsonDocument = new ObjectMapper().readTree(Files.readAllBytes(jsonDocumentPath));
    final byte[] msgpackBytes = MSGPACK_MAPPER.writeValueAsBytes(jsonDocument);
    final MutableDirectBuffer buffer = new UnsafeBuffer(msgpackBytes);
    final MsgPackTree documentTree = indexer.index(buffer);

    // when
    final int resultLength = writer.write(documentTree);

    // then
    assertThat(resultLength).isEqualTo(msgpackBytes.length);
    final MutableDirectBuffer result = writer.getResult();
    assertThat(MSGPACK_MAPPER.readTree(result.byteArray())).isEqualTo(jsonDocument);
  }

  @Test
  public void shouldWriteMsgPackTreeWhenWriterHasSmallInitSize() throws Exception {
    // given
    final MsgPackDocumentTreeWriter writer = new MsgPackDocumentTreeWriter(64);
    final JsonNode jsonDocument = new ObjectMapper().readTree(Files.readAllBytes(jsonDocumentPath));
    final byte[] msgpackBytes = MSGPACK_MAPPER.writeValueAsBytes(jsonDocument);
    assertThat(msgpackBytes.length).isGreaterThan(64);
    final MutableDirectBuffer buffer = new UnsafeBuffer(msgpackBytes);
    final MsgPackTree documentTree = indexer.index(buffer);

    // when
    final int resultLength = writer.write(documentTree);

    // then
    assertThat(resultLength).isEqualTo(msgpackBytes.length);
    final MutableDirectBuffer result = writer.getResult();
    assertThat(result.capacity()).isGreaterThanOrEqualTo(msgpackBytes.length);
    assertThat(MSGPACK_MAPPER.readTree(result.byteArray())).isEqualTo(jsonDocument);
  }
}

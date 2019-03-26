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

import static io.zeebe.msgpack.mapping.MappingBuilder.createMapping;
import static io.zeebe.msgpack.mapping.MappingTestUtil.JSON_MAPPER;
import static io.zeebe.msgpack.mapping.MappingTestUtil.MSGPACK_MAPPER;
import static io.zeebe.msgpack.spec.MsgPackHelper.EMTPY_OBJECT;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.util.buffer.BufferUtil;
import java.io.IOException;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

/** Represents a test class to test the extract document functionality with help of mappings. */
public class MappingExtractTest {
  @Rule public ExpectedException expectedException = ExpectedException.none();

  private MsgPackMergeTool mergeTool = new MsgPackMergeTool(1024);

  @Test
  public void shouldThrowExceptionIfMappingDoesNotMatchInStrictMode() throws Throwable {
    // given variables
    final DirectBuffer sourceDocument = new UnsafeBuffer(EMTPY_OBJECT);
    final Mapping[] mapping = createMapping("foo", "bar");

    // expect
    expectedException.expect(MappingException.class);
    expectedException.expectMessage("No data found for query foo.");

    // when
    mergeTool.mergeDocumentStrictly(sourceDocument, mapping);
  }

  @Test
  public void shouldExtractTwiceWithoutMapping() throws Throwable {
    // given documents
    final DirectBuffer sourceDocument =
        new UnsafeBuffer(
            MSGPACK_MAPPER.writeValueAsBytes(
                JSON_MAPPER.readTree(
                    "{'arr':[{'deepObj':{'value':123}}, 1], 'obj':{'int':1}, 'test':'value'}")));

    // when merge
    mergeTool.mergeDocument(sourceDocument);
    mergeTool.mergeDocument(sourceDocument);
    byte[] result = BufferUtil.bufferAsArray(mergeTool.writeResultToBuffer());

    // then expect result
    assertThat(MSGPACK_MAPPER.readTree(result))
        .isEqualTo(
            JSON_MAPPER.readTree(
                "{'arr':[{'deepObj':{'value':123}}, 1], 'obj':{'int':1}, 'test':'value'}}"));

    // new source and mappings
    sourceDocument.wrap(MSGPACK_MAPPER.writeValueAsBytes(JSON_MAPPER.readTree("{'foo':'bar'}")));

    // when again merge after that
    mergeTool.reset();
    mergeTool.mergeDocument(sourceDocument);
    mergeTool.mergeDocument(sourceDocument);
    result = BufferUtil.bufferAsArray(mergeTool.writeResultToBuffer());

    // then expect result
    assertThat(MSGPACK_MAPPER.readTree(result)).isEqualTo(JSON_MAPPER.readTree("{'foo':'bar'}"));
  }

  @Test
  public void shouldExtractAndMergeMultipleDocuments() {
    // given
    final DirectBuffer document1 = asMsgPack("{'att1':'val1'}");
    final DirectBuffer document2 = asMsgPack("{'att2':'val2'}");
    final DirectBuffer document3 = asMsgPack("{'att3':'val3'}");

    // when
    mergeTool.mergeDocument(document1, createMapping("att1", "newAtt1"));
    mergeTool.mergeDocument(document2, createMapping("att2", "newAtt2"));
    mergeTool.mergeDocument(
        document3, createMapping("att3", "newAtt2")); // overwriting the last one

    // then
    final DirectBuffer mergedDocument = mergeTool.writeResultToBuffer();

    MappingTestUtil.assertThatMsgPack(mergedDocument)
        .hasValue("{'newAtt1':'val1', 'newAtt2':'val3'}");
  }

  @Test
  public void shouldCollectMultipleValuesInArray() {
    // given
    final DirectBuffer document1 = asMsgPack("{'att1':'val1'}");
    final DirectBuffer document2 = asMsgPack("{'att2':'val2'}");
    final DirectBuffer document3 = asMsgPack("{'att3':'val3'}");

    // when
    mergeTool.mergeDocument(document1, createMapping("att1", "array", Mapping.Type.COLLECT));
    mergeTool.mergeDocument(document2, createMapping("att2", "array", Mapping.Type.COLLECT));
    mergeTool.mergeDocument(document3, createMapping("att3", "array", Mapping.Type.COLLECT));

    // then
    final DirectBuffer mergedDocument = mergeTool.writeResultToBuffer();

    MappingTestUtil.assertThatMsgPack(mergedDocument)
        .hasValue("{'array':['val1', 'val2', 'val3']}");
  }

  private static DirectBuffer asMsgPack(String json) {
    try {
      return new UnsafeBuffer(MSGPACK_MAPPER.writeValueAsBytes(JSON_MAPPER.readTree(json)));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}

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
import static io.zeebe.msgpack.spec.MsgPackHelper.NIL;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.util.buffer.BufferUtil;
import java.io.IOException;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

/** Represents a test class to test the merge documents functionality with help of mappings. */
public class MappingMergeTest {
  @Rule public ExpectedException expectedException = ExpectedException.none();

  private MsgPackMergeTool mergeTool = new MsgPackMergeTool(1024);

  @Before
  public void setUp() {
    mergeTool.reset();
  }

  @Test
  public void shouldThrowExceptionOnMergeWhenDocumentIsNull() throws Throwable {
    // expect
    expectedException.expect(RuntimeException.class);
    expectedException.expectMessage("document must not be null");

    // when
    mergeTool.mergeDocument(null);
  }

  @Test
  public void shouldThrowExceptionOnMapAndMergeIfDocumentIsNull() throws Throwable {
    // given variables
    final Mapping[] mapping = createMapping("foo", "bar");

    // expect
    expectedException.expect(RuntimeException.class);
    expectedException.expectMessage("document must not be null");

    // when
    mergeTool.mergeDocument(null, mapping);
  }

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
  public void shouldMergeTwiceWithoutMappings() throws Throwable {
    // given documents
    DirectBuffer sourceDocument =
        new UnsafeBuffer(
            MSGPACK_MAPPER.writeValueAsBytes(JSON_MAPPER.readTree("{'test':'thisValue'}")));

    final DirectBuffer targetDocument =
        new UnsafeBuffer(
            MSGPACK_MAPPER.writeValueAsBytes(
                JSON_MAPPER.readTree("{'arr':[0, 1], 'obj':{'int':1}, 'test':'value'}")));

    // when merge
    mergeTool.mergeDocument(targetDocument);
    mergeTool.mergeDocument(sourceDocument);
    byte result[] = BufferUtil.bufferAsArray(mergeTool.writeResultToBuffer());

    // then expect result
    assertThat(MSGPACK_MAPPER.readTree(result))
        .isEqualTo(JSON_MAPPER.readTree("{'arr':[0, 1], 'obj':{'int':1}, 'test':'thisValue'}"));

    // new source and mappings
    sourceDocument =
        new UnsafeBuffer(
            MSGPACK_MAPPER.writeValueAsBytes(JSON_MAPPER.readTree("{'other':[2, 3]}")));

    targetDocument.wrap(result);

    // when again merge after that
    mergeTool.reset();
    mergeTool.mergeDocument(targetDocument);
    mergeTool.mergeDocument(sourceDocument);
    result = BufferUtil.bufferAsArray(mergeTool.writeResultToBuffer());

    // then expect result
    assertThat(MSGPACK_MAPPER.readTree(result))
        .isEqualTo(
            JSON_MAPPER.readTree(
                "{'arr':[0, 1], 'obj':{'int':1}, 'test':'thisValue', 'other':[2, 3]}"));
  }

  @Test
  public void shouldMergeNilWithEmptyObject() throws Throwable {
    // given variables
    final DirectBuffer sourceDocument = new UnsafeBuffer(NIL);
    final DirectBuffer targetDocument = new UnsafeBuffer(EMTPY_OBJECT);

    // when
    mergeTool.mergeDocument(targetDocument);
    mergeTool.mergeDocument(sourceDocument);

    // then
    final DirectBuffer result = mergeTool.writeResultToBuffer();
    assertThat(result).isEqualByComparingTo(new UnsafeBuffer(EMTPY_OBJECT));
  }

  @Test
  public void shouldMergeEmptyObjectWithNil() throws Throwable {
    // given variables
    final DirectBuffer sourceDocument = new UnsafeBuffer(EMTPY_OBJECT);
    final DirectBuffer targetDocument = new UnsafeBuffer(NIL);

    // when
    mergeTool.mergeDocument(targetDocument);
    mergeTool.mergeDocument(sourceDocument);

    // then
    final DirectBuffer result = mergeTool.writeResultToBuffer();
    assertThat(result).isEqualByComparingTo(new UnsafeBuffer(EMTPY_OBJECT));
  }

  @Test
  public void shouldMergeNilWithNil() throws Throwable {
    // given variables
    final DirectBuffer sourceDocument = new UnsafeBuffer(NIL);
    final DirectBuffer targetDocument = new UnsafeBuffer(NIL);

    // when
    mergeTool.mergeDocument(targetDocument);
    mergeTool.mergeDocument(sourceDocument);

    // then
    final DirectBuffer result = mergeTool.writeResultToBuffer();
    assertThat(result).isEqualByComparingTo(new UnsafeBuffer(NIL));
  }

  @Test
  public void shouldMergeMultipleDocumentsInOnePass() {
    // given
    final DirectBuffer document1 = asMsgPack("{'att1':'val1'}");
    final DirectBuffer document2 = asMsgPack("{'att2':'val2'}");
    final DirectBuffer document3 = asMsgPack("{'att3':'val3'}");

    // when
    mergeTool.mergeDocument(document1);
    mergeTool.mergeDocument(document2);
    mergeTool.mergeDocument(document3);

    // then
    final DirectBuffer mergedDocument = mergeTool.writeResultToBuffer();

    MappingTestUtil.assertThatMsgPack(mergedDocument)
        .hasValue("{'att1':'val1', 'att2':'val2', 'att3':'val3'}");
  }

  private static DirectBuffer asMsgPack(String json) {
    try {
      return new UnsafeBuffer(MSGPACK_MAPPER.writeValueAsBytes(JSON_MAPPER.readTree(json)));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}

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
package io.zeebe.msgpack.query;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.msgpack.filter.ArrayIndexFilter;
import io.zeebe.msgpack.filter.MapValueWithKeyFilter;
import io.zeebe.msgpack.filter.MsgPackFilter;
import io.zeebe.msgpack.filter.RootCollectionFilter;
import io.zeebe.msgpack.filter.WildcardFilter;
import io.zeebe.msgpack.spec.MsgPackToken;
import io.zeebe.msgpack.spec.MsgPackType;
import io.zeebe.msgpack.util.ByteUtil;
import io.zeebe.msgpack.util.MsgPackUtil;
import io.zeebe.msgpack.util.TestUtil;
import java.nio.charset.StandardCharsets;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

public class MsgPackTraverserTest {

  protected MsgPackQueryExecutor valueVisitor = new MsgPackQueryExecutor();
  protected MsgPackTraverser traverser = new MsgPackTraverser();

  @Test
  public void testQuerySingleResult() {
    // given
    final MsgPackFilter[] filters = new MsgPackFilter[2];
    filters[0] = new RootCollectionFilter();
    filters[1] = new MapKeyFilter("foo");

    final MsgPackFilterContext filterInstances = TestUtil.generateDefaultInstances(0, 1);
    valueVisitor.init(filters, filterInstances);

    final DirectBuffer encodedMessage =
        MsgPackUtil.encodeMsgPack(
            (p) -> {
              p.packMapHeader(2);
              p.packString("baz");
              p.packString("foo");
              p.packString("foo");
              p.packString("baz");
            });

    // when
    traverser.wrap(encodedMessage, 0, encodedMessage.capacity());
    final boolean success = traverser.traverse(valueVisitor);

    // then
    assertThat(success).isTrue();
    assertThat(valueVisitor.numResults()).isEqualTo(1);

    valueVisitor.moveToResult(0);
    assertThat(valueVisitor.currentResultPosition()).isEqualTo(9);
    assertThat(valueVisitor.currentResultLength()).isEqualTo(4);
  }

  @Test
  public void testQueryMultipleResult() {
    // given
    final MsgPackFilter[] filters = new MsgPackFilter[2];
    filters[0] = new RootCollectionFilter();
    filters[1] = new MapKeyFilter("foo");

    final MsgPackFilterContext filterInstances = TestUtil.generateDefaultInstances(0, 1);
    valueVisitor.init(filters, filterInstances);

    final DirectBuffer encodedMessage =
        MsgPackUtil.encodeMsgPack(
            (p) -> {
              p.packMapHeader(3); // 1
              p.packString("baz"); // 4
              p.packString("foo"); // 4
              p.packString("foo"); // 4
              p.packString("baz"); // 4
              p.packString("foo"); // 4
              p.packString("baz"); // 4
            });

    // when
    traverser.wrap(encodedMessage, 0, encodedMessage.capacity());
    traverser.traverse(valueVisitor);

    // then
    assertThat(valueVisitor.numResults()).isEqualTo(2);

    valueVisitor.moveToResult(0);
    assertThat(valueVisitor.currentResultPosition()).isEqualTo(9);
    assertThat(valueVisitor.currentResultLength()).isEqualTo(4);

    valueVisitor.moveToResult(1);
    assertThat(valueVisitor.currentResultPosition()).isEqualTo(17);
    assertThat(valueVisitor.currentResultLength()).isEqualTo(4);
  }

  @Test
  public void testNestedQuery() {
    // given
    final MsgPackFilter[] filters = new MsgPackFilter[4];
    filters[0] = new RootCollectionFilter();
    filters[1] = new MapValueWithKeyFilter();
    filters[2] = new ArrayIndexFilter();

    final MsgPackFilterContext filterInstances = TestUtil.generateDefaultInstances(0, 1, 2, 1);
    filterInstances.moveTo(1);
    MapValueWithKeyFilter.encodeDynamicContext(filterInstances.dynamicContext(), "foo");
    filterInstances.moveTo(2);
    ArrayIndexFilter.encodeDynamicContext(filterInstances.dynamicContext(), 1);
    filterInstances.moveTo(3);
    MapValueWithKeyFilter.encodeDynamicContext(filterInstances.dynamicContext(), "bar");

    valueVisitor.init(filters, filterInstances);

    final DirectBuffer encodedMessage =
        MsgPackUtil.encodeMsgPack(
            (p) -> {
              p.packMapHeader(2); // 1  {
              p.packString("NOT_THE_TARGET"); // 15   "":
              p.packString("NOT_THE_TARGET"); // 15   "",
              p.packString("foo"); // 4    "":
              p.packArrayHeader(2); // 1    [
              p.packString("NOT_THE_TARGET"); // 15     "",
              p.packMapHeader(2); // 1      {
              p.packString("NOT_THE_TARGET"); // 15       "":
              p.packString("NOT_THE_TARGET"); // 15       "",
              p.packString("bar"); // 4        "":
              p.packString("THE_TARGET"); // 11       ""}]}
            });

    // when
    traverser.wrap(encodedMessage, 0, encodedMessage.capacity());
    traverser.traverse(valueVisitor);

    // then
    assertThat(valueVisitor.numResults()).isEqualTo(1);

    valueVisitor.moveToResult(0);
    assertThat(valueVisitor.currentResultPosition()).isEqualTo(86);
    assertThat(valueVisitor.currentResultLength()).isEqualTo(11);
  }

  @Test
  public void testQueryMatchingMap() {
    // given
    final MsgPackFilter[] filters = new MsgPackFilter[2];
    filters[0] = new RootCollectionFilter();
    filters[1] = new MapValueWithKeyFilter();

    final MsgPackFilterContext filterInstances = TestUtil.generateDefaultInstances(0, 1);
    MapValueWithKeyFilter.encodeDynamicContext(filterInstances.dynamicContext(), "target");

    valueVisitor.init(filters, filterInstances);

    final DirectBuffer encodedMessage =
        MsgPackUtil.encodeMsgPack(
            (p) -> {
              p.packMapHeader(2); // 1 {
              p.packString("foo"); // 4   "":
              p.packString("foo"); // 4   "",
              p.packString("target"); // 7   "":
              p.packMapHeader(1); // 1   {
              p.packString("foo"); // 4     "":
              p.packString("foo"); // 4     ""}}
            });

    // when
    traverser.wrap(encodedMessage, 0, encodedMessage.capacity());
    traverser.traverse(valueVisitor);

    // then
    assertThat(valueVisitor.numResults()).isEqualTo(1);

    valueVisitor.moveToResult(0);
    assertThat(valueVisitor.currentResultPosition()).isEqualTo(16);
    assertThat(valueVisitor.currentResultLength()).isEqualTo(9);
  }

  @Test
  public void testWildcardFilter() {
    // given
    final MsgPackFilter[] filters = new MsgPackFilter[2];
    filters[0] = new RootCollectionFilter();
    filters[1] = new WildcardFilter();

    final MsgPackFilterContext filterInstances = TestUtil.generateDefaultInstances(0, 1);

    valueVisitor.init(filters, filterInstances);

    final DirectBuffer encodedMessage =
        MsgPackUtil.encodeMsgPack(
            (p) -> {
              p.packMapHeader(2); // 1 {
              p.packString("key1"); // 5   "":
              p.packString("val1"); // 5   "",
              p.packString("key2"); // 5   "":
              p.packString("val2"); // 5   ""}
            });

    // when
    traverser.wrap(encodedMessage, 0, encodedMessage.capacity());
    traverser.traverse(valueVisitor);

    // then
    assertThat(valueVisitor.numResults()).isEqualTo(2);

    valueVisitor.moveToResult(0);
    assertThat(valueVisitor.currentResultPosition()).isEqualTo(6);
    assertThat(valueVisitor.currentResultLength()).isEqualTo(5);

    valueVisitor.moveToResult(1);
    assertThat(valueVisitor.currentResultPosition()).isEqualTo(16);
    assertThat(valueVisitor.currentResultLength()).isEqualTo(5);
  }

  @Test
  public void testInputValidation() {
    // given
    final DirectBuffer encodedValidMessage =
        MsgPackUtil.encodeMsgPack(
            (p) -> {
              p.packString("foo");
            });
    final byte[] bytes = new byte[encodedValidMessage.capacity() + 1];

    encodedValidMessage.getBytes(0, bytes, 0, encodedValidMessage.capacity());
    bytes[bytes.length - 1] =
        (byte) 0xc7; // according to msgpack-spec, this is ext8, but we don't support it

    final UnsafeBuffer encodedInvalidMessage = new UnsafeBuffer(bytes);

    final MsgPackTraverser traverser = new MsgPackTraverser();
    traverser.wrap(encodedInvalidMessage, 0, encodedInvalidMessage.capacity());

    // when
    final boolean success = traverser.traverse((p, c) -> {}); // ignore

    // then
    assertThat(success).isFalse();
    assertThat(traverser.getInvalidPosition()).isEqualTo(encodedValidMessage.capacity());
    assertThat(traverser.getErrorMessage()).isEqualTo("Unknown token format 'EXTENSION'");
  }

  protected static class MapKeyFilter implements MsgPackFilter {
    protected byte[] keyword;

    public MapKeyFilter(String key) {
      keyword = key.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public boolean matches(
        MsgPackTraversalContext ctx, DirectBuffer filterContext, MsgPackToken value) {
      if (ctx.isMap() && ctx.currentElement() % 2 == 0) // => map key has odd index
      {
        if (value.getType() == MsgPackType.STRING) {
          final DirectBuffer encodedString = value.getValueBuffer();
          return ByteUtil.equal(keyword, encodedString, 0, encodedString.capacity());
        }
      }

      return false;
    }
  }
}

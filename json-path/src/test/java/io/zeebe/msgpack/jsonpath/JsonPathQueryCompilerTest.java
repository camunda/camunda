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
package io.zeebe.msgpack.jsonpath;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.msgpack.filter.ArrayIndexFilter;
import io.zeebe.msgpack.filter.MapValueWithKeyFilter;
import io.zeebe.msgpack.filter.MsgPackFilter;
import io.zeebe.msgpack.filter.RootCollectionFilter;
import io.zeebe.msgpack.filter.WildcardFilter;
import io.zeebe.msgpack.query.MsgPackFilterContext;
import io.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.junit.Test;

public class JsonPathQueryCompilerTest {

  @Test
  public void testQueryCompiler() {
    // given
    final JsonPathQueryCompiler compiler = new JsonPathQueryCompiler();

    // when
    final JsonPathQuery jsonPathQuery = compiler.compile("key1.key2.key3");

    // then
    assertThat(jsonPathQuery.isValid()).isTrue();
    assertThat(jsonPathQuery.getVariableName()).isEqualTo(BufferUtil.wrapString("key1"));

    final MsgPackFilter[] filters = jsonPathQuery.getFilters();
    assertThat(filters).hasSize(4);
    // note: these assertions are stricter than necessary;
    // all we need as that each filter is once in the list and
    // that each instance references the correct filter
    assertThat(filters[0]).isInstanceOf(RootCollectionFilter.class);
    assertThat(filters[1]).isInstanceOf(MapValueWithKeyFilter.class);
    assertThat(filters[2]).isInstanceOf(ArrayIndexFilter.class);
    assertThat(filters[3]).isInstanceOf(WildcardFilter.class);

    final MsgPackFilterContext filterInstances = jsonPathQuery.getFilterInstances();
    assertThat(filterInstances.size()).isEqualTo(4);

    assertFilterAtPosition(filterInstances, 0, 0);
    assertFilterAtPosition(filterInstances, 1, 1);
    assertFilterAtPosition(filterInstances, 2, 1);
    assertFilterAtPosition(filterInstances, 3, 1);
  }

  @Test
  public void testQueryProvideUnderlyingExpression() {
    // given
    final String expression = "key1.key2.key3";
    final JsonPathQueryCompiler compiler = new JsonPathQueryCompiler();

    // when
    final JsonPathQuery jsonPathQuery = compiler.compile(expression);

    // then
    final DirectBuffer expressionBuffer = jsonPathQuery.getExpression();
    final byte[] expressionBytes = new byte[expressionBuffer.capacity()];
    expressionBuffer.getBytes(0, expressionBytes);

    assertThat(expressionBytes).isEqualTo(expression.getBytes());
  }

  protected static void assertFilterAtPosition(
      MsgPackFilterContext filterInstances, int position, int expectedFilterId) {
    filterInstances.moveTo(position);
    assertThat(filterInstances.filterId()).isEqualTo(expectedFilterId);
  }
}

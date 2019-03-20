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
package io.zeebe.db.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.zeebe.db.ColumnFamily;
import io.zeebe.db.ZeebeDb;
import io.zeebe.db.ZeebeDbFactory;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class DbStringColumnFamilyTest {

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  private final ZeebeDbFactory<DefaultColumnFamily> dbFactory =
      DefaultZeebeDbFactory.getDefaultFactory(DefaultColumnFamily.class);

  private ZeebeDb<DefaultColumnFamily> zeebeDb;
  private ColumnFamily<DbString, DbString> columnFamily;
  private DbString key;
  private DbString value;

  @Before
  public void setup() throws Exception {
    final File pathName = temporaryFolder.newFolder();
    zeebeDb = dbFactory.createDb(pathName);

    key = new DbString();
    value = new DbString();
    columnFamily =
        zeebeDb.createColumnFamily(
            DefaultColumnFamily.DEFAULT, zeebeDb.createContext(), key, value);
  }

  @Test
  public void shouldPutValue() {
    // given
    key.wrapString("foo");
    value.wrapString("baring");

    // when
    columnFamily.put(key, value);
    value.wrapString("yes");

    // then
    final DbString zbLong = columnFamily.get(key);

    assertThat(zbLong).isNotNull();
    assertThat(zbLong.toString()).isEqualTo("baring");

    // zbLong and value are referencing the same object
    assertThat(value.toString()).isEqualTo("baring");
  }

  @Test
  public void shouldUseForeachValue() {
    // given
    putKeyValuePair("foo", "baring");
    putKeyValuePair("this is the one", "as you know");
    putKeyValuePair("hello", "world");
    putKeyValuePair("another", "string");
    putKeyValuePair("might", "be good");

    // when
    final List<String> values = new ArrayList<>();
    columnFamily.forEach((value) -> values.add(value.toString()));

    // then
    assertThat(values).containsExactly("baring", "world", "be good", "string", "as you know");
  }

  @Test
  public void shouldUseForeachPair() {
    // given
    putKeyValuePair("foo", "baring");
    putKeyValuePair("this is the one", "as you know");
    putKeyValuePair("hello", "world");
    putKeyValuePair("another", "string");
    putKeyValuePair("might", "be good");

    // when
    final List<String> keys = new ArrayList<>();
    final List<String> values = new ArrayList<>();
    columnFamily.forEach(
        (key, value) -> {
          keys.add(key.toString());
          values.add(value.toString());
        });

    // then
    assertThat(values).containsExactly("baring", "world", "be good", "string", "as you know");
    assertThat(keys).containsExactly("foo", "hello", "might", "another", "this is the one");
  }

  @Test
  public void shouldUseWhileTrue() {
    // given
    putKeyValuePair("foo", "baring");
    putKeyValuePair("this is the one", "as you know");
    putKeyValuePair("hello", "world");
    putKeyValuePair("another", "string");
    putKeyValuePair("might", "be good");

    // when
    final List<String> keys = new ArrayList<>();
    final List<String> values = new ArrayList<>();
    columnFamily.whileTrue(
        (key, value) -> {
          keys.add(key.toString());
          values.add(value.toString());

          return !value.toString().equalsIgnoreCase("world");
        });

    // then
    assertThat(values).containsExactly("baring", "world");
    assertThat(keys).containsExactly("foo", "hello");
  }

  @Test
  public void shouldUseWhileEqualPrefix() {
    // given
    putKeyValuePair("foo", "baring");
    putKeyValuePair("this is the one", "as you know");
    putKeyValuePair("hello", "world");
    putKeyValuePair("another", "string");
    putKeyValuePair("and", "be good");

    // when
    key.wrapString("an");
    final List<String> keys = new ArrayList<>();
    final List<String> values = new ArrayList<>();
    columnFamily.whileEqualPrefix(
        key,
        (key, value) -> {
          keys.add(key.toString());
          values.add(value.toString());

          return !key.toString().equalsIgnoreCase("world");
        });

    // then should be empty
    // since whileEqual should work only for composite keys
    // DbString writes length of the string before the content
    // -> that is why prefix search will not work
    assertThat(values).isEmpty();
    assertThat(keys).isEmpty();
  }

  @Test
  public void shouldUseWhileEqualPrefixLikeGet() {
    // given
    putKeyValuePair("foo", "baring");
    putKeyValuePair("this is the one", "as you know");
    putKeyValuePair("hello", "world");
    putKeyValuePair("another", "string");
    putKeyValuePair("and", "be good");

    // when
    key.wrapString("and");
    final List<String> keys = new ArrayList<>();
    final List<String> values = new ArrayList<>();
    columnFamily.whileEqualPrefix(
        key,
        (key, value) -> {
          keys.add(key.toString());
          values.add(value.toString());

          return !key.toString().equalsIgnoreCase("world");
        });

    // then should work like get
    assertThat(values).containsExactly("be good");
    assertThat(keys).containsExactly("and");
  }

  @Test
  public void shouldAllowSingleNestedWhileEqualPrefix() {
    // given
    putKeyValuePair("and", "be good");
    key.wrapString("and");

    // when
    columnFamily.whileEqualPrefix(
        key,
        (key, value) -> {
          columnFamily.whileEqualPrefix(key, (k, v) -> {});
        });

    // then no exception is thrown
  }

  @Test
  public void shouldThrowExceptionOnMultipleNestedWhileEqualPrefix() {
    // given
    putKeyValuePair("and", "be good");
    key.wrapString("and");

    // when
    assertThatThrownBy(
            () ->
                columnFamily.whileEqualPrefix(
                    key,
                    (key, value) -> {
                      columnFamily.whileEqualPrefix(
                          key,
                          (k, v) -> {
                            columnFamily.whileEqualPrefix(key, (k2, v2) -> {});
                          });
                    }))
        .hasRootCauseInstanceOf(IllegalStateException.class)
        .hasMessage("Unexpected error occurred during zeebe db transaction operation.")
        .hasStackTraceContaining(
            "Currently nested prefix iterations are not supported! This will cause unexpected behavior.");
  }

  private void putKeyValuePair(String key, String value) {
    this.key.wrapString(key);
    this.value.wrapString(value);
    columnFamily.put(this.key, this.value);
  }
}

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

public class DbCompositeKeyColumnFamilyTest {

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  private final ZeebeDbFactory<DefaultColumnFamily> dbFactory =
      DefaultZeebeDbFactory.getDefaultFactory(DefaultColumnFamily.class);

  private ZeebeDb<DefaultColumnFamily> zeebeDb;
  private ColumnFamily<DbCompositeKey<DbString, DbLong>, DbString> columnFamily;
  private DbString firstKey;
  private DbLong secondKey;
  private DbCompositeKey<DbString, DbLong> compositeKey;
  private DbString value;

  @Before
  public void setup() throws Exception {
    final File pathName = temporaryFolder.newFolder();
    zeebeDb = dbFactory.createDb(pathName);

    firstKey = new DbString();
    secondKey = new DbLong();
    compositeKey = new DbCompositeKey<>(firstKey, secondKey);
    value = new DbString();
    columnFamily =
        zeebeDb.createColumnFamily(
            DefaultColumnFamily.DEFAULT, zeebeDb.createContext(), compositeKey, value);
  }

  @Test
  public void shouldPutValue() {
    // given
    firstKey.wrapString("foo");
    secondKey.wrapLong(2);
    value.wrapString("baring");

    // when
    columnFamily.put(compositeKey, value);
    value.wrapString("yes");

    // then
    final DbString zbLong = columnFamily.get(compositeKey);

    assertThat(zbLong).isNotNull();
    assertThat(zbLong.toString()).isEqualTo("baring");

    // zbLong and value are referencing the same object
    assertThat(value.toString()).isEqualTo("baring");
  }

  @Test
  public void shouldUseForeachValue() {
    // given
    putKeyValuePair("foo", 12, "baring");
    putKeyValuePair("foo", 13, "different value");
    putKeyValuePair("this is the one", 255, "as you know");
    putKeyValuePair("hello", 34, "world");
    putKeyValuePair("another", 923113, "string");
    putKeyValuePair("might", 37426, "be good");

    // when
    final List<String> values = new ArrayList<>();
    columnFamily.forEach((value) -> values.add(value.toString()));

    // then
    assertThat(values)
        .containsExactly("baring", "different value", "world", "be good", "string", "as you know");
  }

  @Test
  public void shouldUseForeachPair() {
    // given
    putKeyValuePair("foo", 12, "baring");
    putKeyValuePair("foo", 13, "different value");
    putKeyValuePair("this is the one", 255, "as you know");
    putKeyValuePair("hello", 34, "world");
    putKeyValuePair("another", 923113, "string");
    putKeyValuePair("might", 37426, "be good");

    // when
    final List<String> firstKeyParts = new ArrayList<>();
    final List<Long> secondKeyParts = new ArrayList<>();
    final List<String> values = new ArrayList<>();
    columnFamily.forEach(
        (key, value) -> {
          final DbString firstPart = key.getFirst();
          firstKeyParts.add(firstPart.toString());

          final DbLong secondPart = key.getSecond();
          secondKeyParts.add(secondPart.getValue());

          values.add(value.toString());
        });

    // then
    assertThat(values)
        .containsExactly("baring", "different value", "world", "be good", "string", "as you know");
    assertThat(firstKeyParts)
        .containsExactly("foo", "foo", "hello", "might", "another", "this is the one");
    assertThat(secondKeyParts).containsExactly(12L, 13L, 34L, 37426L, 923113L, 255L);
  }

  @Test
  public void shouldUseForeachToDelete() {
    // given
    putKeyValuePair("foo", 12, "baring");
    putKeyValuePair("foo", 13, "different value");
    putKeyValuePair("this is the one", 255, "as you know");
    putKeyValuePair("hello", 34, "world");
    putKeyValuePair("another", 923113, "string");
    putKeyValuePair("might", 37426, "be good");

    // when
    final List<String> firstKeyParts = new ArrayList<>();
    final List<Long> secondKeyParts = new ArrayList<>();
    final List<String> values = new ArrayList<>();
    columnFamily.forEach((key, value) -> columnFamily.delete(key));

    columnFamily.forEach(
        (key, value) -> {
          final DbString firstPart = key.getFirst();
          firstKeyParts.add(firstPart.toString());

          final DbLong secondPart = key.getSecond();
          secondKeyParts.add(secondPart.getValue());

          values.add(value.toString());
        });

    // then
    assertThat(values).isEmpty();
    assertThat(firstKeyParts).isEmpty();
    assertThat(secondKeyParts).isEmpty();
  }

  @Test
  public void shouldUseWhileTrue() {
    // given
    putKeyValuePair("foo", 12, "baring");
    putKeyValuePair("foo", 13, "different value");
    putKeyValuePair("this is the one", 255, "as you know");
    putKeyValuePair("hello", 34, "world");
    putKeyValuePair("another", 923113, "string");
    putKeyValuePair("might", 37426, "be good");

    // when
    final List<String> firstKeyParts = new ArrayList<>();
    final List<Long> secondKeyParts = new ArrayList<>();
    final List<String> values = new ArrayList<>();
    columnFamily.whileTrue(
        (key, value) -> {
          final DbString firstPart = key.getFirst();
          firstKeyParts.add(firstPart.toString());

          final DbLong secondPart = key.getSecond();
          secondKeyParts.add(secondPart.getValue());

          values.add(value.toString());

          return !value.toString().equalsIgnoreCase("world");
        });

    // then
    assertThat(values).containsExactly("baring", "different value", "world");
    assertThat(firstKeyParts).containsExactly("foo", "foo", "hello");
    assertThat(secondKeyParts).containsExactly(12L, 13L, 34L);
  }

  @Test
  public void shouldUseWhileTrueToDelete() {
    // given
    putKeyValuePair("foo", 12, "baring");
    putKeyValuePair("foo", 13, "different value");
    putKeyValuePair("this is the one", 255, "as you know");
    putKeyValuePair("hello", 34, "world");
    putKeyValuePair("another", 923113, "string");
    putKeyValuePair("might", 37426, "be good");

    // when
    final List<String> firstKeyParts = new ArrayList<>();
    final List<Long> secondKeyParts = new ArrayList<>();
    final List<String> values = new ArrayList<>();
    columnFamily.whileTrue(
        (key, value) -> {
          columnFamily.delete(key);
          return key.getSecond().getValue() != 13;
        });

    columnFamily.forEach(
        (key, value) -> {
          final DbString firstPart = key.getFirst();
          firstKeyParts.add(firstPart.toString());

          final DbLong secondPart = key.getSecond();
          secondKeyParts.add(secondPart.getValue());

          values.add(value.toString());
        });

    // then
    assertThat(values).containsExactly("world", "be good", "string", "as you know");
    assertThat(firstKeyParts).containsExactly("hello", "might", "another", "this is the one");
    assertThat(secondKeyParts).containsExactly(34L, 37426L, 923113L, 255L);
  }

  @Test
  public void shouldUseWhileEqualPrefix() {
    // given
    putKeyValuePair("foo", 12, "baring");
    putKeyValuePair("foobar", 53, "expected value");
    putKeyValuePair("foo", 13, "different value");
    putKeyValuePair("foo", 213, "oh wow");
    putKeyValuePair("foo", 53, "expected value");
    putKeyValuePair("this is the one", 255, "as you know");
    putKeyValuePair("hello", 34, "world");
    putKeyValuePair("another", 923113, "string");
    putKeyValuePair("might", 37426, "be good");

    // when
    firstKey.wrapString("foo");
    final List<String> firstKeyParts = new ArrayList<>();
    final List<Long> secondKeyParts = new ArrayList<>();
    final List<String> values = new ArrayList<>();
    columnFamily.whileEqualPrefix(
        firstKey,
        (key, value) -> {
          final DbString firstPart = key.getFirst();
          firstKeyParts.add(firstPart.toString());

          final DbLong secondPart = key.getSecond();
          secondKeyParts.add(secondPart.getValue());

          values.add(value.toString());
        });

    // then
    assertThat(values).containsExactly("baring", "different value", "expected value", "oh wow");
    assertThat(firstKeyParts).containsOnly("foo");
    assertThat(secondKeyParts).containsExactly(12L, 13L, 53L, 213L);
  }

  @Test
  public void shouldUseGetWhileIterating() {
    // given
    putKeyValuePair("foo", 12, "baring");
    putKeyValuePair("foobar", 53, "expected value");
    putKeyValuePair("foo", 13, "different value");
    putKeyValuePair("foo", 213, "oh wow");
    putKeyValuePair("foo", 53, "expected value");
    putKeyValuePair("this is the one", 255, "as you know");
    putKeyValuePair("hello", 213, "world");
    putKeyValuePair("another", 923113, "string");
    putKeyValuePair("hello", 13, "foo");
    putKeyValuePair("might", 37426, "be good");

    // when
    firstKey.wrapString("foo");
    final List<String> values = new ArrayList<>();
    final List<String> seenStringKeys = new ArrayList<>();
    final List<Long> seenLongKeys = new ArrayList<>();
    columnFamily.whileEqualPrefix(
        firstKey,
        (key, value) -> {
          seenStringKeys.add(key.getFirst().toString());
          seenLongKeys.add(key.getSecond().getValue());

          key.getFirst().wrapString("hello");
          final DbString zbString = columnFamily.get(key);
          if (zbString != null) {
            values.add(zbString.toString());
          }
        });

    // then
    assertThat(values).containsExactly("foo", "world");
    assertThat(seenStringKeys).containsOnly("foo");
    assertThat(seenLongKeys).containsExactly(12L, 13L, 53L, 213L);
  }

  @Test
  public void shouldUseWhileEqualPrefixAndTrue() {
    // given
    putKeyValuePair("foo", 12, "baring");
    putKeyValuePair("foobar", 53, "expected value");
    putKeyValuePair("foo", 13, "different value");
    putKeyValuePair("foo", 213, "oh wow");
    putKeyValuePair("foo", 53, "expected value");
    putKeyValuePair("this is the one", 255, "as you know");
    putKeyValuePair("hello", 34, "world");
    putKeyValuePair("another", 923113, "string");
    putKeyValuePair("might", 37426, "be good");

    // when
    firstKey.wrapString("foo");
    final List<String> firstKeyParts = new ArrayList<>();
    final List<Long> secondKeyParts = new ArrayList<>();
    final List<String> values = new ArrayList<>();
    columnFamily.whileEqualPrefix(
        firstKey,
        (key, value) -> {
          final DbString firstPart = key.getFirst();
          firstKeyParts.add(firstPart.toString());

          final DbLong secondPart = key.getSecond();
          secondKeyParts.add(secondPart.getValue());

          values.add(value.toString());

          return secondPart.getValue() < 50;
        });

    // then
    assertThat(values).containsExactly("baring", "different value", "expected value");
    assertThat(firstKeyParts).containsOnly("foo");
    assertThat(secondKeyParts).containsExactly(12L, 13L, 53L);
  }

  @Test
  public void shouldUseWhileEqualPrefixToDelete() {
    // given
    putKeyValuePair("foo", 12, "baring");
    putKeyValuePair("foo", 13, "different value");
    putKeyValuePair("this is the one", 255, "as you know");
    putKeyValuePair("hello", 34, "world");
    putKeyValuePair("another", 923113, "string");
    putKeyValuePair("might", 37426, "be good");

    // when
    firstKey.wrapString("foo");
    columnFamily.whileEqualPrefix(
        firstKey,
        (key, value) -> {
          columnFamily.delete(key);
          return key.getSecond().getValue() != 13;
        });

    final List<String> firstKeyParts = new ArrayList<>();
    final List<Long> secondKeyParts = new ArrayList<>();
    final List<String> values = new ArrayList<>();
    columnFamily.forEach(
        (key, value) -> {
          final DbString firstPart = key.getFirst();
          firstKeyParts.add(firstPart.toString());

          final DbLong secondPart = key.getSecond();
          secondKeyParts.add(secondPart.getValue());

          values.add(value.toString());
        });

    // then
    assertThat(values).containsExactly("world", "be good", "string", "as you know");
    assertThat(firstKeyParts).containsExactly("hello", "might", "another", "this is the one");
    assertThat(secondKeyParts).containsExactly(34L, 37426L, 923113L, 255L);
  }

  private void putKeyValuePair(String firstKey, long secondKey, String value) {
    this.firstKey.wrapString(firstKey);
    this.secondKey.wrapLong(secondKey);

    this.value.wrapString(value);
    columnFamily.put(this.compositeKey, this.value);
  }
}

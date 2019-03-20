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

public class ColumnFamilyTest {

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  private final ZeebeDbFactory<DefaultColumnFamily> dbFactory =
      DefaultZeebeDbFactory.getDefaultFactory(DefaultColumnFamily.class);

  private ZeebeDb<DefaultColumnFamily> zeebeDb;
  private ColumnFamily<DbLong, DbLong> columnFamily;
  private DbLong key;
  private DbLong value;

  @Before
  public void setup() throws Exception {
    final File pathName = temporaryFolder.newFolder();
    zeebeDb = dbFactory.createDb(pathName);

    key = new DbLong();
    value = new DbLong();
    columnFamily =
        zeebeDb.createColumnFamily(
            DefaultColumnFamily.DEFAULT, zeebeDb.createContext(), key, value);
  }

  @Test
  public void shouldPutValue() {
    // given
    key.wrapLong(1213);
    value.wrapLong(255);

    // when
    columnFamily.put(key, value);
    value.wrapLong(221);

    // then
    final DbLong zbLong = columnFamily.get(key);

    assertThat(zbLong).isNotNull();
    assertThat(zbLong.getValue()).isEqualTo(255);

    // zbLong and value are referencing the same object
    assertThat(value.getValue()).isEqualTo(255);
  }

  @Test
  public void shouldReturnNullIfNotExist() {
    // given
    key.wrapLong(1213);

    // when
    final DbLong zbLong = columnFamily.get(key);

    // then
    assertThat(zbLong).isNull();
  }

  @Test
  public void shouldPutMultipleValues() {
    // given
    putKeyValuePair(1213, 255);

    // when
    putKeyValuePair(456789, 12345);
    value.wrapLong(221);

    // then
    key.wrapLong(1213);
    DbLong longValue = columnFamily.get(key);

    assertThat(longValue).isNotNull();
    assertThat(longValue.getValue()).isEqualTo(255);

    key.wrapLong(456789);
    longValue = columnFamily.get(key);

    assertThat(longValue).isNotNull();
    assertThat(longValue.getValue()).isEqualTo(12345);
  }

  @Test
  public void shouldPutAndGetMultipleValues() {
    // given
    putKeyValuePair(1213, 255);

    // when
    DbLong longValue = columnFamily.get(key);
    putKeyValuePair(456789, 12345);
    value.wrapLong(221);

    // then
    assertThat(longValue.getValue()).isEqualTo(221);
    key.wrapLong(1213);
    longValue = columnFamily.get(key);

    assertThat(longValue).isNotNull();
    assertThat(longValue.getValue()).isEqualTo(255);

    key.wrapLong(456789);
    longValue = columnFamily.get(key);

    assertThat(longValue).isNotNull();
    assertThat(longValue.getValue()).isEqualTo(12345);
  }

  @Test
  public void shouldCheckForExistence() {
    // given
    putKeyValuePair(1213, 255);

    // when
    final boolean exists = columnFamily.exists(key);

    // then
    assertThat(exists).isTrue();
  }

  @Test
  public void shouldNotExist() {
    // given
    key.wrapLong(1213);

    // when
    final boolean exists = columnFamily.exists(key);

    // then
    assertThat(exists).isFalse();
  }

  @Test
  public void shouldDelete() {
    // given
    putKeyValuePair(1213, 255);

    // when
    columnFamily.delete(key);

    // then
    final boolean exists = columnFamily.exists(key);
    assertThat(exists).isFalse();

    final DbLong zbLong = columnFamily.get(key);
    assertThat(zbLong).isNull();
  }

  @Test
  public void shouldNotDeleteDifferentKey() {
    // given
    putKeyValuePair(1213, 255);

    // when
    key.wrapLong(700);
    columnFamily.delete(key);

    // then
    key.wrapLong(1213);
    final boolean exists = columnFamily.exists(key);
    assertThat(exists).isTrue();

    final DbLong zbLong = columnFamily.get(key);
    assertThat(zbLong).isNotNull();
    assertThat(zbLong.getValue()).isEqualTo(255);
  }

  @Test
  public void shouldUseForeachValue() {
    // given
    putKeyValuePair(4567, 123);
    putKeyValuePair(6734, 921);
    putKeyValuePair(1213, 255);
    putKeyValuePair(1, Short.MAX_VALUE);
    putKeyValuePair(Short.MAX_VALUE, 1);

    // when
    final List<Long> values = new ArrayList<>();
    columnFamily.forEach((value) -> values.add(value.getValue()));

    // then
    assertThat(values).containsExactly((long) Short.MAX_VALUE, 255L, 123L, 921L, 1L);
  }

  @Test
  public void shouldUseForeachPair() {
    // given
    putKeyValuePair(4567, 123);
    putKeyValuePair(6734, 921);
    putKeyValuePair(1213, 255);
    putKeyValuePair(1, Short.MAX_VALUE);
    putKeyValuePair(Short.MAX_VALUE, 1);

    // when
    final List<Long> keys = new ArrayList<>();
    final List<Long> values = new ArrayList<>();
    columnFamily.forEach(
        (key, value) -> {
          keys.add(key.getValue());
          values.add(value.getValue());
        });

    // then
    assertThat(keys).containsExactly(1L, 1213L, 4567L, 6734L, (long) Short.MAX_VALUE);
    assertThat(values).containsExactly((long) Short.MAX_VALUE, 255L, 123L, 921L, 1L);
  }

  @Test
  public void shouldDeleteOnForeachPair() {
    // given
    putKeyValuePair(4567, 123);
    putKeyValuePair(6734, 921);
    putKeyValuePair(1213, 255);
    putKeyValuePair(1, Short.MAX_VALUE);
    putKeyValuePair(Short.MAX_VALUE, 1);

    // when
    columnFamily.forEach(
        (key, value) -> {
          columnFamily.delete(key);
        });

    final List<Long> keys = new ArrayList<>();
    final List<Long> values = new ArrayList<>();
    columnFamily.forEach(
        (key, value) -> {
          keys.add(key.getValue());
          values.add(value.getValue());
        });

    // then
    assertThat(keys).isEmpty();
    assertThat(values).isEmpty();
    key.wrapLong(4567L);
    assertThat(columnFamily.exists(key)).isFalse();

    key.wrapLong(6734);
    assertThat(columnFamily.exists(key)).isFalse();

    key.wrapLong(1213);
    assertThat(columnFamily.exists(key)).isFalse();

    key.wrapLong(1);
    assertThat(columnFamily.exists(key)).isFalse();

    key.wrapLong(Short.MAX_VALUE);
    assertThat(columnFamily.exists(key)).isFalse();
  }

  @Test
  public void shouldUseWhileTrue() {
    // given
    putKeyValuePair(4567, 123);
    putKeyValuePair(6734, 921);
    putKeyValuePair(1213, 255);
    putKeyValuePair(1, Short.MAX_VALUE);
    putKeyValuePair(Short.MAX_VALUE, 1);

    // when
    final List<Long> keys = new ArrayList<>();
    final List<Long> values = new ArrayList<>();
    columnFamily.whileTrue(
        (key, value) -> {
          keys.add(key.getValue());
          values.add(value.getValue());

          return key.getValue() != 4567;
        });

    // then
    assertThat(keys).containsExactly(1L, 1213L, 4567L);
    assertThat(values).containsExactly((long) Short.MAX_VALUE, 255L, 123L);
  }

  @Test
  public void shouldDeleteWhileTrue() {
    // given
    putKeyValuePair(4567, 123);
    putKeyValuePair(6734, 921);
    putKeyValuePair(1213, 255);
    putKeyValuePair(1, Short.MAX_VALUE);
    putKeyValuePair(Short.MAX_VALUE, 1);

    // when
    columnFamily.whileTrue(
        (key, value) -> {
          columnFamily.delete(key);
          return key.getValue() != 4567;
        });

    final List<Long> keys = new ArrayList<>();
    final List<Long> values = new ArrayList<>();
    columnFamily.forEach(
        (key, value) -> {
          keys.add(key.getValue());
          values.add(value.getValue());
        });

    // then
    assertThat(keys).containsExactly(6734L, (long) Short.MAX_VALUE);
    assertThat(values).containsExactly(921L, 1L);
  }

  @Test
  public void shouldCheckIfEmpty() {
    assertThat(columnFamily.isEmpty()).isTrue();

    putKeyValuePair(1, 10);
    assertThat(columnFamily.isEmpty()).isFalse();

    columnFamily.delete(key);
    assertThat(columnFamily.isEmpty()).isTrue();
  }

  private void putKeyValuePair(int key, int value) {
    this.key.wrapLong(key);
    this.value.wrapLong(value);
    columnFamily.put(this.key, this.value);
  }
}

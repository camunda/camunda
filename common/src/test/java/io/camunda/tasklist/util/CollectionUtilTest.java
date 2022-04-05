/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class CollectionUtilTest {

  @Test
  public void testAsMapOneEntry() {
    final Map<String, Object> result = CollectionUtil.asMap("key1", "value1");
    assertThat(result).hasSize(1);
    assertThat(result).containsEntry("key1", "value1");
  }

  @Test
  public void testAsMapManyEntries() {
    final Map<String, Object> result =
        CollectionUtil.asMap("key1", "value1", "key2", "value2", "key3", "value3");
    assertThat(result).hasSize(3);
    assertThat(result).containsEntry("key2", "value2");
    assertThat(result).containsEntry("key3", "value3");
  }

  @Test
  public void testAsMapException() {
    assertThatExceptionOfType(TasklistRuntimeException.class)
        .isThrownBy(() -> CollectionUtil.asMap((Object[]) null));
    assertThatExceptionOfType(TasklistRuntimeException.class)
        .isThrownBy(() -> CollectionUtil.asMap("key1"));
    assertThatExceptionOfType(TasklistRuntimeException.class)
        .isThrownBy(() -> CollectionUtil.asMap("key1", "value1", "key2"));
  }

  @Test
  public void testFromTo() {
    assertThat(CollectionUtil.fromTo(0, 0)).contains(0);
    assertThat(CollectionUtil.fromTo(0, -1)).isEmpty();
    assertThat(CollectionUtil.fromTo(-1, 0)).contains(-1, 0);
    assertThat(CollectionUtil.fromTo(1, 5)).contains(1, 2, 3, 4, 5);
  }

  @Test
  public void testWithoutNulls() {
    final List<Object> ids = Arrays.asList("id-1", null, "id3", null, null, "id5");
    assertThat(CollectionUtil.withoutNulls(ids)).containsExactly("id-1", "id3", "id5");
  }

  @Test
  public void testToSafeListOfStrings() {
    final List<Object> ids = Arrays.asList("id-1", null, "id3", null, null, "id5");
    assertThat(CollectionUtil.withoutNulls(ids)).containsExactly("id-1", "id3", "id5");
  }

  @Test
  public void testSplitAndGetSublist() {
    List<Integer> partitions = Arrays.asList(1, 2, 3, 4, 5, 6);
    assertThat(CollectionUtil.splitAndGetSublist(partitions, 2, 1))
        .containsExactlyInAnyOrder(4, 5, 6);

    partitions = Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8);
    assertThat(CollectionUtil.splitAndGetSublist(partitions, 3, 0))
        .containsExactlyInAnyOrder(1, 2, 3);
    assertThat(CollectionUtil.splitAndGetSublist(partitions, 3, 1))
        .containsExactlyInAnyOrder(4, 5, 6);
    assertThat(CollectionUtil.splitAndGetSublist(partitions, 3, 2)).containsExactlyInAnyOrder(7, 8);

    partitions = Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8);
    assertThat(CollectionUtil.splitAndGetSublist(partitions, 3, 4)).isEmpty();
  }
}

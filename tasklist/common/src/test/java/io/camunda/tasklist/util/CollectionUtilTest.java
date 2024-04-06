/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.tasklist.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CollectionUtilTest {

  @Test
  void testAsMapOneEntry() {
    final Map<String, Object> result = CollectionUtil.asMap("key1", "value1");
    assertThat(result).hasSize(1);
    assertThat(result).containsEntry("key1", "value1");
  }

  @Test
  void testAsMapManyEntries() {
    final Map<String, Object> result =
        CollectionUtil.asMap("key1", "value1", "key2", "value2", "key3", "value3");
    assertThat(result).hasSize(3);
    assertThat(result).containsEntry("key2", "value2");
    assertThat(result).containsEntry("key3", "value3");
  }

  @Test
  void testAsMapException() {
    assertThatExceptionOfType(TasklistRuntimeException.class)
        .isThrownBy(() -> CollectionUtil.asMap((Object[]) null));
    assertThatExceptionOfType(TasklistRuntimeException.class)
        .isThrownBy(() -> CollectionUtil.asMap("key1"));
    assertThatExceptionOfType(TasklistRuntimeException.class)
        .isThrownBy(() -> CollectionUtil.asMap("key1", "value1", "key2"));
  }

  @Test
  void testFromTo() {
    assertThat(CollectionUtil.fromTo(0, 0)).contains(0);
    assertThat(CollectionUtil.fromTo(0, -1)).isEmpty();
    assertThat(CollectionUtil.fromTo(-1, 0)).contains(-1, 0);
    assertThat(CollectionUtil.fromTo(1, 5)).contains(1, 2, 3, 4, 5);
  }

  @Test
  void testWithoutNulls() {
    final List<Object> ids = Arrays.asList("id-1", null, "id3", null, null, "id5");
    assertThat(CollectionUtil.withoutNulls(ids)).containsExactly("id-1", "id3", "id5");
  }

  @Test
  void testToSafeListOfStrings() {
    final List<Object> ids = Arrays.asList("id-1", null, "id3", null, null, "id5");
    assertThat(CollectionUtil.withoutNulls(ids)).containsExactly("id-1", "id3", "id5");
  }

  @Test
  void testSplitAndGetSublist() {
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

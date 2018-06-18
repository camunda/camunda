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
package io.zeebe.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class CollectionUtilTest {

  @Test
  public void addToMapOfLists() {
    // given
    final Map<String, List<String>> map = new HashMap<>();

    // when
    CollectionUtil.addToMapOfLists(map, "foo", "bar");

    // then
    assertThat(map).containsExactly(entry("foo", Arrays.asList("bar")));
  }

  @Test
  public void appendToMapOfLists() {
    // given
    final Map<String, List<String>> map = new HashMap<>();
    CollectionUtil.addToMapOfLists(map, "foo", "bar");

    // when
    CollectionUtil.addToMapOfLists(map, "foo", "baz");

    // then
    assertThat(map).containsExactly(entry("foo", Arrays.asList("bar", "baz")));
  }
}

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
package io.camunda.process.test.impl.mock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import io.camunda.process.test.api.mock.VariablesTable;
import io.camunda.process.test.api.mock.VariablesTable.Builder;
import io.camunda.process.test.api.mock.VariablesTable.Row;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class VariablesTableTest {

  @Test
  void shouldBuildRowsInOrder() {
    // given/when
    final VariablesTable table =
        VariablesTable.when(Collections.singletonMap("loopCounter", 1))
            .thenReturn(Collections.singletonMap("sampleType", "regolith"))
            .when(Collections.singletonMap("loopCounter", 2))
            .thenReturn(Collections.singletonMap("sampleType", "basalt"));

    // then
    assertThat(table.getRows()).hasSize(2);

    final Row row0 = table.getRows().get(0);
    assertThat(row0.getWhen()).containsExactly(entry("loopCounter", 1));
    assertThat(row0.getVariables()).containsExactly(entry("sampleType", "regolith"));

    final Row row1 = table.getRows().get(1);
    assertThat(row1.getWhen()).containsExactly(entry("loopCounter", 2));
    assertThat(row1.getVariables()).containsExactly(entry("sampleType", "basalt"));
  }

  @Test
  void shouldNotChangeReusedPartialTableWhenExtendedAgain() {
    // given
    final Builder base =
        VariablesTable.when(Collections.singletonMap("loopCounter", 1))
            .thenReturn(Collections.singletonMap("a", 1));

    // when: the partial table is reused in two different chains
    final VariablesTable branchA =
        base.when(Collections.singletonMap("loopCounter", 2))
            .thenReturn(Collections.singletonMap("b", 2));
    final VariablesTable branchB =
        base.when(Collections.singletonMap("loopCounter", 3))
            .thenReturn(Collections.singletonMap("c", 3));

    // then: the base table is untouched, and each branch only has its own extra row
    assertThat(base.getRows()).hasSize(1);
    assertThat(branchA.getRows()).hasSize(2);
    assertThat(branchB.getRows()).hasSize(2);
    assertThat(branchA.getRows().get(1).getVariables()).containsExactly(entry("b", 2));
    assertThat(branchB.getRows().get(1).getVariables()).containsExactly(entry("c", 3));
  }

  @Test
  void shouldKeepNullValueInsideWhen() {
    // given
    final Map<String, Object> when = new HashMap<>();
    when.put("errorCode", null);

    // when
    final VariablesTable table =
        VariablesTable.when(when).thenReturn(Collections.singletonMap("handled", true));

    // then
    assertThat(table.getRows().get(0).getWhen()).containsOnlyKeys("errorCode");
    assertThat(table.getRows().get(0).getWhen().get("errorCode")).isNull();
  }

  @Test
  void shouldReturnUnmodifiableRows() {
    // given
    final VariablesTable table =
        VariablesTable.when(Collections.emptyMap()).thenReturn(Collections.singletonMap("a", 1));

    // when/then
    assertThatThrownBy(() -> table.getRows().add(null))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void shouldRejectNullWhen() {
    // when/then
    assertThatThrownBy(() -> VariablesTable.when(null)).isInstanceOf(NullPointerException.class);
  }

  @Test
  void shouldRejectNullVariables() {
    // when/then
    assertThatThrownBy(() -> VariablesTable.when(Collections.emptyMap()).thenReturn(null))
        .isInstanceOf(NullPointerException.class);
  }
}

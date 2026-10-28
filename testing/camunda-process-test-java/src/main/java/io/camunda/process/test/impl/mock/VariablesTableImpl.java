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

import io.camunda.process.test.api.mock.VariablesTable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable, copy-on-append implementation of {@link VariablesTable.Builder}. Every {@code
 * thenReturn} call returns a new instance with one more row, so a partially built table that is
 * reused in multiple chains is never changed by a later chain.
 */
public final class VariablesTableImpl implements VariablesTable.Builder {

  public static final VariablesTableImpl EMPTY = new VariablesTableImpl(Collections.emptyList());

  private final List<VariablesTable.Row> rows;

  private VariablesTableImpl(final List<VariablesTable.Row> rows) {
    this.rows = Collections.unmodifiableList(rows);
  }

  @Override
  public List<VariablesTable.Row> getRows() {
    return rows;
  }

  @Override
  public VariablesTable.Then when(final Map<String, Object> match) {
    Objects.requireNonNull(match, "'when' must not be null");

    return variables -> {
      Objects.requireNonNull(variables, "'variables' must not be null");

      final List<VariablesTable.Row> newRows = new ArrayList<>(rows);
      newRows.add(new RowImpl(copyOf(match), copyOf(variables)));
      return new VariablesTableImpl(newRows);
    };
  }

  /**
   * Copies the given map into an unmodifiable map that tolerates {@code null} values. {@code
   * Map.copyOf}/{@code Map.of}/{@code Collectors.toMap} all reject {@code null} values, which would
   * break the "a {@code null} value matches {@code null}" matching rule.
   */
  private static Map<String, Object> copyOf(final Map<String, Object> source) {
    return Collections.unmodifiableMap(new LinkedHashMap<>(source));
  }

  private static final class RowImpl implements VariablesTable.Row {

    private final Map<String, Object> when;
    private final Map<String, Object> variables;

    private RowImpl(final Map<String, Object> when, final Map<String, Object> variables) {
      this.when = when;
      this.variables = variables;
    }

    @Override
    public Map<String, Object> getWhen() {
      return when;
    }

    @Override
    public Map<String, Object> getVariables() {
      return variables;
    }
  }
}

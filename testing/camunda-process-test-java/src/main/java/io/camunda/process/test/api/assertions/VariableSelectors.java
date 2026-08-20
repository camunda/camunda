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
package io.camunda.process.test.api.assertions;

import io.camunda.client.api.search.filter.VariableFilter;
import io.camunda.client.api.search.response.Variable;
import java.util.Objects;

/** A collection of predefined {@link VariableSelector}s. */
public class VariableSelectors {

  /**
   * Select the variable by its name.
   *
   * @param name the name of the variable.
   * @return the selector
   */
  public static VariableSelector byName(final String name) {
    return new VariableNameSelector(name);
  }

  /**
   * Select the variable by its scope key.
   *
   * @param scopeKey the scope key of the variable.
   * @return the selector
   */
  public static VariableSelector byScopeKey(final long scopeKey) {
    return new VariableScopeKeySelector(scopeKey);
  }

  /**
   * Select the variable by its process instance key.
   *
   * @param processInstanceKey the process instance key of the variable.
   * @return the selector
   */
  public static VariableSelector byProcessInstanceKey(final long processInstanceKey) {
    return new VariableProcessInstanceKeySelector(processInstanceKey);
  }

  /**
   * Select the variable by a substring of its value.
   *
   * @param substring the substring to search for in the variable value.
   * @return the selector
   */
  public static VariableSelector byValueContains(final String substring) {
    return new VariableValueContainsSelector(substring);
  }

  private static final class VariableNameSelector implements VariableSelector {

    private final String name;

    private VariableNameSelector(final String name) {
      this.name = name;
    }

    @Override
    public boolean test(final Variable variable) {
      return name.equals(variable.getName());
    }

    @Override
    public String describe() {
      return name;
    }

    @Override
    public void applyFilter(final VariableFilter filter) {
      filter.name(name);
    }
  }

  private static final class VariableScopeKeySelector implements VariableSelector {

    private final long scopeKey;

    private VariableScopeKeySelector(final long scopeKey) {
      this.scopeKey = scopeKey;
    }

    @Override
    public boolean test(final Variable variable) {
      return Objects.equals(variable.getScopeKey(), scopeKey);
    }

    @Override
    public String describe() {
      return String.format("scopeKey: %d", scopeKey);
    }

    @Override
    public void applyFilter(final VariableFilter filter) {
      filter.scopeKey(scopeKey);
    }
  }

  private static final class VariableValueContainsSelector implements VariableSelector {

    private final String substring;

    private VariableValueContainsSelector(final String substring) {
      this.substring = substring;
    }

    @Override
    public boolean test(final Variable variable) {
      return variable.getValue() != null && variable.getValue().contains(substring);
    }

    @Override
    public String describe() {
      return String.format("value contains: %s", substring);
    }

    @Override
    public void applyFilter(final VariableFilter filter) {
      filter.value(v -> v.like("*" + substring + "*"));
    }
  }

  private static final class VariableProcessInstanceKeySelector implements VariableSelector {

    private final long processInstanceKey;

    private VariableProcessInstanceKeySelector(final long processInstanceKey) {
      this.processInstanceKey = processInstanceKey;
    }

    @Override
    public boolean test(final Variable variable) {
      return Objects.equals(variable.getProcessInstanceKey(), processInstanceKey);
    }

    @Override
    public String describe() {
      return String.format("processInstanceKey: %d", processInstanceKey);
    }

    @Override
    public void applyFilter(final VariableFilter filter) {
      filter.processInstanceKey(processInstanceKey);
    }
  }
}

/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.exporter.record;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.zeebe.broker.exporter.ExporterObjectMapper;
import io.zeebe.exporter.api.record.RecordValueWithVariables;
import java.util.Map;
import java.util.Objects;

public abstract class RecordValueWithVariablesImpl extends RecordValueImpl
    implements RecordValueWithVariables {
  protected final String variables;

  public RecordValueWithVariablesImpl(
      final ExporterObjectMapper objectMapper, final String variables) {
    super(objectMapper);
    this.variables = variables;
  }

  @Override
  public String getVariables() {
    return variables;
  }

  @Override
  @JsonIgnore
  public Map<String, Object> getVariablesAsMap() {
    return objectMapper.fromJsonAsMap(variables);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final RecordValueWithVariablesImpl that = (RecordValueWithVariablesImpl) o;
    return Objects.equals(variables, that.variables);
  }

  @Override
  public int hashCode() {
    return Objects.hash(variables);
  }
}

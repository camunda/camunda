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
import io.zeebe.exporter.record.RecordValueWithPayload;
import java.util.Map;
import java.util.Objects;

public abstract class RecordValueWithPayloadImpl extends RecordValueImpl
    implements RecordValueWithPayload {
  protected final String payload;

  public RecordValueWithPayloadImpl(final ExporterObjectMapper objectMapper, final String payload) {
    super(objectMapper);
    this.payload = payload;
  }

  @Override
  public String getPayload() {
    return payload;
  }

  @Override
  @JsonIgnore
  public Map<String, Object> getPayloadAsMap() {
    return objectMapper.fromJsonAsMap(payload);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final RecordValueWithPayloadImpl that = (RecordValueWithPayloadImpl) o;
    return Objects.equals(payload, that.payload);
  }

  @Override
  public int hashCode() {
    return Objects.hash(payload);
  }
}

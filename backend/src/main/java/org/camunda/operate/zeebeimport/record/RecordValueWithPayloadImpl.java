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
package org.camunda.operate.zeebeimport.record;

import java.util.Map;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.zeebe.exporter.record.RecordValue;
import io.zeebe.exporter.record.RecordValueWithPayload;

public abstract class RecordValueWithPayloadImpl implements RecordValue, RecordValueWithPayload {
  private String payload;

  public RecordValueWithPayloadImpl() {
  }

  @Override
  public String getPayload() {
    return payload;
  }

  public void setPayload(String payload) {
    this.payload = payload;
  }

  @Override
  @JsonIgnore
  public Map<String, Object> getPayloadAsMap() {
    throw new UnsupportedOperationException("getPayloadAsMap operation is not supported");
  }

  @Override
  public String toJson() {
    throw new UnsupportedOperationException("toJson operation is not supported");
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

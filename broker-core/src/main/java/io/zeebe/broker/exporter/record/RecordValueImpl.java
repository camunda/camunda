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

import io.zeebe.broker.exporter.ExporterObjectMapper;
import io.zeebe.exporter.api.record.RecordValue;

public abstract class RecordValueImpl implements RecordValue {
  protected final ExporterObjectMapper objectMapper;

  public RecordValueImpl(ExporterObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public String toJson() {
    return objectMapper.toJson(this);
  }
}

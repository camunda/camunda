/*
 * Zeebe Workflow Engine
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
package io.zeebe.engine.processor;

import io.zeebe.protocol.impl.record.RecordMetadata;
import java.util.Objects;

/**
 * Implement to control which events should be handled by a {@link StreamProcessor} based on the
 * event's metadata.
 */
@FunctionalInterface
public interface MetadataFilter {
  /**
   * @param metadata the metadata of the event to be processed next
   * @return true to mark the event for processing; false to skip it
   * @throws RuntimeException to signal that processing cannot continue
   */
  boolean applies(RecordMetadata metadata);

  default MetadataFilter and(MetadataFilter other) {
    Objects.requireNonNull(other);
    return (e) -> this.applies(e) && other.applies(e);
  }
}

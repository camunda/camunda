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

import io.zeebe.logstreams.log.LoggedEvent;
import java.util.Objects;

/** Implement to control which events should be handled by a {@link StreamProcessor}. */
@FunctionalInterface
public interface EventFilter {

  /**
   * @param event the event to be processed next
   * @return true to mark an event for processing; false to skip it
   * @throws RuntimeException to signal that processing cannot continue
   */
  boolean applies(LoggedEvent event);

  default EventFilter and(EventFilter other) {
    Objects.requireNonNull(other);
    return (e) -> applies(e) && other.applies(e);
  }
}

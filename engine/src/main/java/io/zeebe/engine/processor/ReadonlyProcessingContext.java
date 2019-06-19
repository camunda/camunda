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

import io.zeebe.db.DbContext;
import io.zeebe.engine.state.ZeebeState;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LogStreamReader;
import io.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.zeebe.protocol.record.ValueType;
import io.zeebe.util.metrics.MetricsManager;
import io.zeebe.util.sched.ActorControl;
import java.util.Map;
import java.util.function.BooleanSupplier;

public interface ReadonlyProcessingContext {

  /** @return the actor on which the processing runs */
  ActorControl getActor();

  /** @return the filter, which is used to filter for events */
  EventFilter getEventFilter();

  /** @return the logstream, on which the processor runs */
  LogStream getLogStream();

  /** @return the reader, which is used by the processor to read next events */
  LogStreamReader getLogStreamReader();

  /** @return the writer, which is used by the processor to write follow up events */
  TypedStreamWriter getLogStreamWriter();

  /** @return the cache, which contains the mapping from ValueType to UnpackedObject (record) */
  Map<ValueType, UnifiedRecordValue> getEventCache();

  /** @return the map of processors, which are executed during processing */
  RecordProcessorMap getRecordProcessorMap();

  /** @return the state, where the data is stored during processing */
  ZeebeState getZeebeState();

  /** @return the database context for the current actor */
  DbContext getDbContext();

  /** @return the response writer, which is used during processing */
  CommandResponseWriter getCommandResponseWriter();

  /** @return condition which indicates, whether the processing should stop or not */
  BooleanSupplier getAbortCondition();

  /** @return the metrics manager to create new metrics */
  MetricsManager getMetricsManager();
}

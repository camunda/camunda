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
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.zeebe.util.metrics.MetricsManager;
import io.zeebe.util.sched.ActorControl;
import java.util.Map;
import java.util.function.BooleanSupplier;

public class ProcessingContext implements ReadonlyProcessingContext {

  private ActorControl actor;
  private MetricsManager metricsManager;
  private int producerId;
  private String streamProcessorName;
  private EventFilter eventFilter;
  private LogStream logStream;
  private LogStreamReader logStreamReader;
  private TypedStreamWriter logStreamWriter;
  private CommandResponseWriter commandResponseWriter;

  private Map<ValueType, UnifiedRecordValue> eventCache;
  private RecordProcessorMap recordProcessorMap;
  private ZeebeState zeebeState;
  private DbContext dbContext;

  private BooleanSupplier abortCondition;

  public ProcessingContext actor(ActorControl actor) {
    this.actor = actor;
    return this;
  }

  public ProcessingContext eventFilter(EventFilter eventFilter) {
    this.eventFilter = eventFilter;
    return this;
  }

  public ProcessingContext logStream(LogStream logStream) {
    this.logStream = logStream;
    return this;
  }

  public ProcessingContext logStreamReader(LogStreamReader logStreamReader) {
    this.logStreamReader = logStreamReader;
    return this;
  }

  public ProcessingContext eventCache(Map<ValueType, UnifiedRecordValue> eventCache) {
    this.eventCache = eventCache;
    return this;
  }

  public ProcessingContext recordProcessorMap(RecordProcessorMap recordProcessorMap) {
    this.recordProcessorMap = recordProcessorMap;
    return this;
  }

  public ProcessingContext zeebeState(ZeebeState zeebeState) {
    this.zeebeState = zeebeState;
    return this;
  }

  public ProcessingContext dbContext(DbContext dbContext) {
    this.dbContext = dbContext;
    return this;
  }

  public ProcessingContext abortCondition(BooleanSupplier abortCondition) {
    this.abortCondition = abortCondition;
    return this;
  }

  public ProcessingContext producerId(int producerId) {
    this.producerId = producerId;
    return this;
  }

  public ProcessingContext streamProcessorName(String streamProcessorName) {
    this.streamProcessorName = streamProcessorName;
    return this;
  }

  public ProcessingContext logStreamWriter(TypedStreamWriter logStreamWriter) {
    this.logStreamWriter = logStreamWriter;
    return this;
  }

  public ProcessingContext commandResponseWriter(CommandResponseWriter commandResponseWriter) {
    this.commandResponseWriter = commandResponseWriter;
    return this;
  }

  public ProcessingContext metricsManager(MetricsManager metricsManager) {
    this.metricsManager = metricsManager;
    return this;
  }

  public ActorControl getActor() {
    return actor;
  }

  public int getProducerId() {
    return producerId;
  }

  public String getStreamProcessorName() {
    return streamProcessorName;
  }

  public EventFilter getEventFilter() {
    return eventFilter;
  }

  public LogStream getLogStream() {
    return logStream;
  }

  public LogStreamReader getLogStreamReader() {
    return logStreamReader;
  }

  public TypedStreamWriter getLogStreamWriter() {
    return logStreamWriter;
  }

  public Map<ValueType, UnifiedRecordValue> getEventCache() {
    return eventCache;
  }

  public RecordProcessorMap getRecordProcessorMap() {
    return recordProcessorMap;
  }

  public ZeebeState getZeebeState() {
    return zeebeState;
  }

  public DbContext getDbContext() {
    return dbContext;
  }

  public CommandResponseWriter getCommandResponseWriter() {
    return commandResponseWriter;
  }

  public BooleanSupplier getAbortCondition() {
    return abortCondition;
  }

  public MetricsManager getMetricsManager() {
    return metricsManager;
  }
}

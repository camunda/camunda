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
package io.zeebe.broker.logstreams.processor;

import io.zeebe.broker.clustering.orchestration.id.IdRecord;
import io.zeebe.broker.clustering.orchestration.topic.TopicRecord;
import io.zeebe.broker.incident.data.IncidentRecord;
import io.zeebe.broker.job.data.JobRecord;
import io.zeebe.broker.system.workflow.repository.data.DeploymentRecord;
import io.zeebe.broker.workflow.data.WorkflowInstanceRecord;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.transport.ServerOutput;
import java.util.EnumMap;

public class TypedStreamEnvironment {
  protected final ServerOutput output;
  protected final LogStream stream;
  protected static final EnumMap<ValueType, Class<? extends UnpackedObject>> EVENT_REGISTRY =
      new EnumMap<>(ValueType.class);

  static {
    EVENT_REGISTRY.put(ValueType.TOPIC, TopicRecord.class);
    EVENT_REGISTRY.put(ValueType.DEPLOYMENT, DeploymentRecord.class);
    EVENT_REGISTRY.put(ValueType.JOB, JobRecord.class);
    EVENT_REGISTRY.put(ValueType.WORKFLOW_INSTANCE, WorkflowInstanceRecord.class);
    EVENT_REGISTRY.put(ValueType.INCIDENT, IncidentRecord.class);
    EVENT_REGISTRY.put(ValueType.ID, IdRecord.class);
  }

  private TypedStreamReader reader;
  private TypedStreamWriter writer;

  public TypedStreamEnvironment(LogStream stream, ServerOutput output) {
    this.output = output;
    this.stream = stream;
  }

  public EnumMap<ValueType, Class<? extends UnpackedObject>> getEventRegistry() {
    return EVENT_REGISTRY;
  }

  public ServerOutput getOutput() {
    return output;
  }

  public LogStream getStream() {
    return stream;
  }

  public TypedEventStreamProcessorBuilder newStreamProcessor() {
    return new TypedEventStreamProcessorBuilder(this);
  }

  public TypedStreamWriter buildStreamWriter() {
    return new TypedStreamWriterImpl(stream, EVENT_REGISTRY);
  }

  public TypedStreamReader buildStreamReader() {
    return new TypedStreamReaderImpl(stream, EVENT_REGISTRY);
  }

  public TypedStreamReader getStreamReader() {
    if (reader == null) {
      reader = buildStreamReader();
    }
    return reader;
  }

  public TypedStreamWriter getStreamWriter() {
    if (writer == null) {
      writer = buildStreamWriter();
    }
    return writer;
  }
}

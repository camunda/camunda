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

import io.zeebe.broker.exporter.stream.ExporterRecord;
import io.zeebe.broker.incident.data.IncidentRecord;
import io.zeebe.broker.subscription.message.data.MessageSubscriptionRecord;
import io.zeebe.broker.subscription.message.data.WorkflowInstanceSubscriptionRecord;
import io.zeebe.broker.workflow.data.TimerRecord;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.zeebe.protocol.impl.record.value.job.JobBatchRecord;
import io.zeebe.protocol.impl.record.value.job.JobRecord;
import io.zeebe.protocol.impl.record.value.message.MessageRecord;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.transport.ServerOutput;
import java.util.EnumMap;

public class TypedStreamEnvironment {
  protected final ServerOutput output;
  protected final LogStream stream;
  protected static final EnumMap<ValueType, Class<? extends UnpackedObject>> EVENT_REGISTRY =
      new EnumMap<>(ValueType.class);

  static {
    EVENT_REGISTRY.put(ValueType.DEPLOYMENT, DeploymentRecord.class);
    EVENT_REGISTRY.put(ValueType.JOB, JobRecord.class);
    EVENT_REGISTRY.put(ValueType.WORKFLOW_INSTANCE, WorkflowInstanceRecord.class);
    EVENT_REGISTRY.put(ValueType.INCIDENT, IncidentRecord.class);
    EVENT_REGISTRY.put(ValueType.MESSAGE, MessageRecord.class);
    EVENT_REGISTRY.put(ValueType.MESSAGE_SUBSCRIPTION, MessageSubscriptionRecord.class);
    EVENT_REGISTRY.put(
        ValueType.WORKFLOW_INSTANCE_SUBSCRIPTION, WorkflowInstanceSubscriptionRecord.class);
    EVENT_REGISTRY.put(ValueType.EXPORTER, ExporterRecord.class);
    EVENT_REGISTRY.put(ValueType.JOB_BATCH, JobBatchRecord.class);
    EVENT_REGISTRY.put(ValueType.TIMER, TimerRecord.class);
  }

  private TypedStreamReader reader;

  public TypedStreamEnvironment(final LogStream stream, final ServerOutput output) {
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

  public TypedCommandWriter buildCommandWriter() {
    return new TypedCommandWriterImpl(stream, EVENT_REGISTRY);
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
}

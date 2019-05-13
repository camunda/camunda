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

import io.zeebe.engine.Loggers;
import io.zeebe.engine.state.ZeebeState;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LogStreamRecordWriter;
import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.protocol.WorkflowInstanceRelated;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.impl.record.RecordMetadata;
import io.zeebe.protocol.impl.record.value.error.ErrorRecord;
import io.zeebe.protocol.intent.ErrorIntent;
import io.zeebe.protocol.intent.Intent;
import io.zeebe.protocol.intent.WorkflowInstanceRelatedIntent;
import io.zeebe.util.ReflectUtil;
import io.zeebe.util.sched.ActorControl;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import org.slf4j.Logger;

@SuppressWarnings({"unchecked"})
public class TypedStreamProcessor implements StreamProcessor {

  private static final Logger LOG = Loggers.WORKFLOW_PROCESSOR_LOGGER;

  protected final RecordProcessorMap recordProcessors;
  protected final List<StreamProcessorLifecycleAware> lifecycleListeners = new ArrayList<>();
  protected final ZeebeState zeebeState;

  private final ErrorRecord errorRecord = new ErrorRecord();
  protected final RecordMetadata metadata = new RecordMetadata();
  protected final EnumMap<ValueType, Class<? extends UnpackedObject>> eventRegistry;
  protected final EnumMap<ValueType, UnpackedObject> eventCache;

  protected final TypedEventImpl typedEvent = new TypedEventImpl();
  private final TypedStreamEnvironment environment;
  private final CommandResponseWriter commandResponseWriter;

  private DelegatingEventProcessor eventProcessorWrapper;
  private ActorControl actor;
  private StreamProcessorContext streamProcessorContext;
  private TypedStreamWriterImpl streamWriter;

  public TypedStreamProcessor(
      final CommandResponseWriter commandResponseWriter,
      final RecordProcessorMap recordProcessors,
      final List<StreamProcessorLifecycleAware> lifecycleListeners,
      final EnumMap<ValueType, Class<? extends UnpackedObject>> eventRegistry,
      final ZeebeState zeebeState,
      final TypedStreamEnvironment environment) {
    this.commandResponseWriter = commandResponseWriter;
    this.recordProcessors = recordProcessors;
    this.zeebeState = zeebeState;
    recordProcessors.values().forEachRemaining(p -> this.lifecycleListeners.add(p));

    this.lifecycleListeners.addAll(lifecycleListeners);

    this.eventCache = new EnumMap<>(ValueType.class);

    eventRegistry.forEach((t, c) -> eventCache.put(t, ReflectUtil.newInstance(c)));
    this.eventRegistry = eventRegistry;
    this.environment = environment;
  }

  @Override
  public void onOpen(final StreamProcessorContext context) {
    final LogStream logStream = context.getLogStream();
    this.streamWriter = new TypedStreamWriterImpl(logStream, eventRegistry);

    this.eventProcessorWrapper =
        new DelegatingEventProcessor(
            context.getId(), commandResponseWriter, logStream, streamWriter, zeebeState);

    this.actor = context.getActorControl();
    this.streamProcessorContext = context;
    lifecycleListeners.forEach(e -> e.onOpen(this));
  }

  @Override
  public void onRecovered() {
    lifecycleListeners.forEach(e -> e.onRecovered(this));
  }

  @Override
  public void onClose() {
    lifecycleListeners.forEach(e -> e.onClose());
  }

  @Override
  public long getPositionToRecoverFrom() {
    return zeebeState.getLastSuccessfuProcessedRecordPosition();
  }

  @Override
  public long getFailedPosition(LoggedEvent currentEvent) {
    metadata.reset();
    currentEvent.readMetadata(metadata);

    if (metadata.getValueType() == ValueType.ERROR) {
      currentEvent.readValue(errorRecord);
      return errorRecord.getErrorEventPosition();
    }
    return -1;
  }

  @Override
  public EventProcessor onEvent(final LoggedEvent event) {
    final long position = event.getPosition();
    metadata.reset();
    event.readMetadata(metadata);

    final TypedRecordProcessor<?> currentProcessor =
        recordProcessors.get(
            metadata.getRecordType(), metadata.getValueType(), metadata.getIntent().value());

    if (currentProcessor != null) {
      final UnpackedObject value = eventCache.get(metadata.getValueType());
      value.reset();
      event.readValue(value);

      typedEvent.wrap(event, metadata, value);
      eventProcessorWrapper.wrap(currentProcessor, typedEvent, position);
      return eventProcessorWrapper;
    } else {
      return null;
    }
  }

  public static class DelegatingEventProcessor implements EventProcessor {

    static final String PROCESSING_ERROR_MESSAGE =
        "Expected to process event '%s' without errors, but exception occurred with message '%s' .";

    final ErrorRecord errorRecord = new ErrorRecord();
    final int streamProcessorId;
    protected final LogStream logStream;
    protected final TypedStreamWriterImpl writer;
    protected final TypedResponseWriterImpl responseWriter;
    private final ZeebeState zeebeState;

    TypedRecordProcessor<?> eventProcessor;
    protected TypedEventImpl event;
    private SideEffectProducer sideEffectProducer;
    private long position;

    public DelegatingEventProcessor(
        final int streamProcessorId,
        final CommandResponseWriter commandResponseWriter,
        final LogStream logStream,
        final TypedStreamWriterImpl writer,
        final ZeebeState zeebeState) {
      this.streamProcessorId = streamProcessorId;
      this.logStream = logStream;
      this.writer = writer;
      this.responseWriter =
          new TypedResponseWriterImpl(commandResponseWriter, logStream.getPartitionId());
      this.zeebeState = zeebeState;
    }

    public void wrap(
        final TypedRecordProcessor<?> eventProcessor,
        final TypedEventImpl event,
        final long position) {
      this.eventProcessor = eventProcessor;
      this.event = event;
      this.position = position;
    }

    @Override
    public void processEvent() {
      resetOutput();

      // default side effect is responses; can be changed by processor
      sideEffectProducer = responseWriter;

      final boolean isNotOnBlacklist = !zeebeState.isOnBlacklist(event);
      if (isNotOnBlacklist) {
        eventProcessor.processRecord(
            position, event, responseWriter, writer, this::setSideEffectProducer);
      }

      zeebeState.markAsProcessed(position);
    }

    @Override
    public void onError(Throwable exception) {
      resetOutput();

      writeRejectionOnCommand(exception);
      errorRecord.initErrorRecord(exception, event.getPosition());

      final Intent intent = event.getMetadata().getIntent();
      if (shouldBeBlacklisted(intent)) {
        final UnpackedObject value = event.getValue();
        if (value instanceof WorkflowInstanceRelated) {
          final long workflowInstanceKey =
              ((WorkflowInstanceRelated) value).getWorkflowInstanceKey();
          zeebeState.blacklist(workflowInstanceKey);
          errorRecord.setWorkflowInstanceKey(workflowInstanceKey);
        }
      }

      writer.appendFollowUpEvent(event.getKey(), ErrorIntent.CREATED, errorRecord);
    }

    private void writeRejectionOnCommand(Throwable exception) {
      final String errorMessage =
          String.format(PROCESSING_ERROR_MESSAGE, event, exception.getMessage());
      LOG.error(errorMessage, exception);

      if (event.metadata.getRecordType() == RecordType.COMMAND) {
        sendCommandRejectionOnException(errorMessage);
        writeCommandRejectionOnException(errorMessage);
      }
    }

    private boolean shouldBeBlacklisted(Intent intent) {

      if (isWorkflowInstanceRelated(intent)) {
        final WorkflowInstanceRelatedIntent workflowInstanceRelatedIntent =
            (WorkflowInstanceRelatedIntent) intent;

        return workflowInstanceRelatedIntent.shouldBlacklistInstanceOnError();
      }

      return false;
    }

    private boolean isWorkflowInstanceRelated(Intent intent) {
      return intent instanceof WorkflowInstanceRelatedIntent;
    }

    private void resetOutput() {
      responseWriter.reset();
      writer.reset();
      this.writer.configureSourceContext(streamProcessorId, position);
    }

    private void writeCommandRejectionOnException(String errorMessage) {
      writer.appendRejection(event, RejectionType.PROCESSING_ERROR, errorMessage);
    }

    private void sendCommandRejectionOnException(String errorMessage) {
      responseWriter.writeRejectionOnCommand(event, RejectionType.PROCESSING_ERROR, errorMessage);
    }

    public void setSideEffectProducer(final SideEffectProducer sideEffectProducer) {
      this.sideEffectProducer = sideEffectProducer;
    }

    @Override
    public boolean executeSideEffects() {
      return sideEffectProducer.flush();
    }

    @Override
    public long writeEvent(final LogStreamRecordWriter writer) {
      return this.writer.flush();
    }
  }

  public ActorControl getActor() {
    return actor;
  }

  public StreamProcessorContext getStreamProcessorContext() {
    return streamProcessorContext;
  }

  public TypedStreamEnvironment getEnvironment() {
    return environment;
  }

  public KeyGenerator getKeyGenerator() {
    return zeebeState.getKeyGenerator();
  }

  public TypedStreamWriterImpl getStreamWriter() {
    return streamWriter;
  }
}

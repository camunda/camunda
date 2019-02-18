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
package io.zeebe.broker.exporter.stream;

import io.zeebe.broker.exporter.ExporterObjectMapper;
import io.zeebe.broker.exporter.context.ExporterContext;
import io.zeebe.broker.exporter.record.RecordMetadataImpl;
import io.zeebe.broker.exporter.repo.ExporterDescriptor;
import io.zeebe.broker.exporter.stream.ExporterRecord.ExporterPosition;
import io.zeebe.db.ZeebeDb;
import io.zeebe.exporter.context.Controller;
import io.zeebe.exporter.record.Record;
import io.zeebe.exporter.spi.Exporter;
import io.zeebe.logstreams.log.LogStreamReader;
import io.zeebe.logstreams.log.LogStreamRecordWriter;
import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.logstreams.processor.EventProcessor;
import io.zeebe.logstreams.processor.StreamProcessor;
import io.zeebe.logstreams.processor.StreamProcessorContext;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.impl.record.RecordMetadata;
import io.zeebe.protocol.intent.ExporterIntent;
import io.zeebe.util.buffer.BufferUtil;
import io.zeebe.util.sched.ActorControl;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.slf4j.LoggerFactory;

public class ExporterStreamProcessor implements StreamProcessor {

  private final RecordMetadata rawMetadata = new RecordMetadata();
  private final List<ExporterContainer> containers;
  private final int partitionId;

  private final ExporterStreamProcessorState state;
  private final RecordExporter recordExporter = new RecordExporter();
  private final ExporterRecordProcessor exporterRecordProcessor = new ExporterRecordProcessor();

  private ActorControl actorControl;
  private LogStreamReader logStreamReader;

  public ExporterStreamProcessor(
      ZeebeDb<ExporterColumnFamilies> zeebeDb,
      final int partitionId,
      final Collection<ExporterDescriptor> descriptors) {
    state = new ExporterStreamProcessorState(zeebeDb);

    this.partitionId = partitionId;

    this.containers = new ArrayList<>(descriptors.size());
    for (final ExporterDescriptor descriptor : descriptors) {
      this.containers.add(new ExporterContainer(descriptor));
    }
  }

  public ExporterStreamProcessorState getState() {
    return state;
  }

  @Override
  public EventProcessor onEvent(LoggedEvent event) {
    final EventProcessor processor;
    event.readMetadata(rawMetadata);

    if (rawMetadata.getValueType() == ValueType.EXPORTER) {
      exporterRecordProcessor.wrap(event);
      processor = exporterRecordProcessor;
    } else {
      recordExporter.wrap(event);
      processor = recordExporter;
    }

    return processor;
  }

  @Override
  public void onOpen(StreamProcessorContext context) {
    logStreamReader = context.getLogStreamReader();
    actorControl = context.getActorControl();

    for (final ExporterContainer container : containers) {
      container.exporter.configure(container.context);
    }
  }

  @Override
  public void onRecovered() {
    long lowestPosition = -1;

    for (final ExporterContainer container : containers) {
      container.exporter.open(container);
      container.position = state.getPosition(container.getId());

      if (lowestPosition == -1 || lowestPosition > container.position) {
        lowestPosition = container.position;
      }
    }

    // in case the lowest known position is not found, start from the
    // beginning again
    if (lowestPosition <= 0 || !logStreamReader.seek(lowestPosition)) {
      logStreamReader.seekToFirstEvent();
    } else {
      if (logStreamReader.hasNext()) {
        logStreamReader.seek(lowestPosition + 1);
      }
    }
  }

  @Override
  public void onClose() {
    for (final ExporterContainer container : containers) {
      try {
        container.exporter.close();
      } catch (final Exception e) {
        container.context.getLogger().error("Error on close", e);
      }
    }
  }

  private boolean shouldCommitPositions() {
    return false;
  }

  private class ExporterContainer implements Controller {
    private static final String LOGGER_NAME_FORMAT = "io.zeebe.broker.exporter.%s";

    private final ExporterContext context;
    private final Exporter exporter;
    private long position;

    ExporterContainer(ExporterDescriptor descriptor) {
      context =
          new ExporterContext(
              LoggerFactory.getLogger(String.format(LOGGER_NAME_FORMAT, descriptor.getId())),
              descriptor.getConfiguration());
      exporter = descriptor.newInstance();
    }

    @Override
    public void updateLastExportedRecordPosition(final long position) {
      actorControl.run(
          () -> {
            state.setPosition(getId(), position);
            this.position = position;
          });
    }

    @Override
    public void scheduleTask(final Duration delay, final Runnable task) {
      actorControl.runDelayed(delay, task);
    }

    private String getId() {
      return context.getConfiguration().getId();
    }
  }

  private class ExporterRecordProcessor implements EventProcessor {
    private final ExporterRecord record = new ExporterRecord();

    public void wrap(final LoggedEvent event) {
      event.readValue(record);
    }

    @Override
    public void processEvent() {
      for (final ExporterPosition position : record.getPositions()) {
        state.setPositionIfGreater(position.getId(), position.getPosition());
      }
    }
  }

  private class RecordExporter implements EventProcessor {
    private final ExporterObjectMapper objectMapper = new ExporterObjectMapper();
    private final ExporterRecordMapper recordMapper = new ExporterRecordMapper(objectMapper);
    private Record record;
    private boolean shouldExecuteSideEffects;
    private int exporterIndex;

    void wrap(LoggedEvent rawEvent) {
      final RecordMetadataImpl metadata =
          new RecordMetadataImpl(
              objectMapper,
              partitionId,
              rawMetadata.getIntent(),
              rawMetadata.getRecordType(),
              rawMetadata.getRejectionType(),
              BufferUtil.bufferAsString(rawMetadata.getRejectionReason()),
              rawMetadata.getValueType());

      record = recordMapper.map(rawEvent, metadata);
      exporterIndex = 0;
      shouldExecuteSideEffects = record != null;
    }

    @Override
    public boolean executeSideEffects() {
      if (!shouldExecuteSideEffects) {
        return true;
      }

      final int exportersCount = containers.size();

      // current error handling strategy is simply to repeat forever until the record can be
      // successfully exported.
      while (exporterIndex < exportersCount) {
        final ExporterContainer container = containers.get(exporterIndex);

        try {
          if (container.position < record.getPosition()) {
            container.exporter.export(record);
          }

          exporterIndex++;
        } catch (final Exception ex) {
          container.context.getLogger().error("Error exporting record {}", record, ex);
          return false;
        }
      }

      return true;
    }

    @Override
    public long writeEvent(LogStreamRecordWriter writer) {
      if (shouldCommitPositions()) {
        final ExporterRecord record = state.newExporterRecord();

        rawMetadata
            .reset()
            .recordType(RecordType.EVENT)
            .valueType(ValueType.EXPORTER)
            .intent(ExporterIntent.EXPORTED);

        return writer.positionAsKey().valueWriter(record).metadataWriter(rawMetadata).tryWrite();
      }

      return 0;
    }
  }
}

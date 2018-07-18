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
package io.zeebe.broker.clustering.orchestration.state;

import static io.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.zeebe.broker.Loggers;
import io.zeebe.broker.clustering.orchestration.topic.TopicRecord;
import io.zeebe.broker.logstreams.processor.SideEffectProducer;
import io.zeebe.broker.logstreams.processor.TypedRecord;
import io.zeebe.broker.logstreams.processor.TypedRecordProcessor;
import io.zeebe.broker.logstreams.processor.TypedResponseWriter;
import io.zeebe.broker.logstreams.processor.TypedResponseWriterImpl;
import io.zeebe.broker.logstreams.processor.TypedStreamWriter;
import io.zeebe.logstreams.processor.EventLifecycleContext;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.intent.TopicIntent;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.agrona.DirectBuffer;
import org.slf4j.Logger;

public class TopicCreateProcessor implements TypedRecordProcessor<TopicRecord> {
  private static final Logger LOG = Loggers.CLUSTERING_LOGGER;

  private final Predicate<DirectBuffer> topicExists;
  private final Consumer<DirectBuffer> notifyListeners;
  private final BiConsumer<Long, TopicRecord> addTopic;

  public TopicCreateProcessor(
      final Predicate<DirectBuffer> topicExists,
      final Consumer<DirectBuffer> notifyListeners,
      final BiConsumer<Long, TopicRecord> addTopic) {
    this.topicExists = topicExists;
    this.notifyListeners = notifyListeners;
    this.addTopic = addTopic;
  }

  @Override
  public void processRecord(
      TypedRecord<TopicRecord> command,
      TypedResponseWriter responseWriter,
      TypedStreamWriter streamWriter,
      Consumer<SideEffectProducer> sideEffect,
      EventLifecycleContext ctx) {

    final TopicRecord topicEvent = command.getValue();
    final DirectBuffer topicName = topicEvent.getName();

    RejectionType rejectionType = null;
    String rejectionReason = null;

    if (topicExists.test(topicName)) {
      rejectionReason = "Topic exists already";
      rejectionType = RejectionType.NOT_APPLICABLE;
    } else if (topicEvent.getPartitions() < 1) {
      rejectionReason = "Topic must have at least one partition";
      rejectionType = RejectionType.BAD_VALUE;
    } else if (topicEvent.getReplicationFactor() < 1) {
      rejectionReason = "Topic must have at least one replica";
      rejectionType = RejectionType.BAD_VALUE;
    }

    if (rejectionType == null) {
      LOG.info("Creating topic {}", topicEvent);
      acceptCommand(command, responseWriter, streamWriter, sideEffect);
    } else {
      LOG.warn("Rejecting topic {} creation: {}", bufferAsString(topicName), rejectionReason);
      rejectCommand(command, responseWriter, streamWriter, rejectionType, rejectionReason);
    }
  }

  private void acceptCommand(
      TypedRecord<TopicRecord> command,
      TypedResponseWriter responseWriter,
      TypedStreamWriter streamWriter,
      Consumer<SideEffectProducer> sideEffect) {

    final long key = streamWriter.writeNewEvent(TopicIntent.CREATING, command.getValue());
    responseWriter.writeEventOnCommand(key, TopicIntent.CREATING, command);

    sideEffect.accept(
        () -> {
          final boolean written = ((TypedResponseWriterImpl) responseWriter).flush();

          if (written) {
            notifyListeners.accept(command.getValue().getName());
          }

          return written;
        });

    addTopic.accept(key, command.getValue());
  }

  private void rejectCommand(
      TypedRecord<TopicRecord> command,
      TypedResponseWriter responseWriter,
      TypedStreamWriter streamWriter,
      RejectionType rejectionType,
      String rejectionReason) {
    responseWriter.writeRejectionOnCommand(command, rejectionType, rejectionReason);
    streamWriter.writeRejection(command, rejectionType, rejectionReason);
  }
}

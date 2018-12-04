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

import io.zeebe.logstreams.state.StateController;
import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.intent.Intent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public class TypedEventStreamProcessorBuilder {
  protected final TypedStreamEnvironment environment;

  protected RecordProcessorMap eventProcessors = new RecordProcessorMap();
  protected List<StreamProcessorLifecycleAware> lifecycleListeners = new ArrayList<>();

  private KeyGenerator keyGenerator;

  public TypedEventStreamProcessorBuilder(TypedStreamEnvironment environment) {
    this.environment = environment;
  }

  // TODO: could remove the ValueType argument as it follows from the intent
  public TypedEventStreamProcessorBuilder onEvent(
      ValueType valueType, Intent intent, TypedRecordProcessor<?> processor) {
    return onRecord(RecordType.EVENT, valueType, intent, processor);
  }

  public <T extends UnpackedObject> TypedEventStreamProcessorBuilder onEvent(
      ValueType valueType,
      Intent intent,
      Predicate<T> activationFunction,
      TypedRecordProcessor<T> processor) {
    return onEvent(
        valueType,
        intent,
        new DelegatingEventProcessor<T>(
            r -> activationFunction.test(r.getValue()) ? processor : null));
  }

  public TypedEventStreamProcessorBuilder onEvent(
      ValueType valueType, Intent intent, Consumer<? extends UnpackedObject> consumer) {
    return onEvent(valueType, intent, new ConsumerProcessor<>(consumer));
  }

  private TypedEventStreamProcessorBuilder onRecord(
      RecordType recordType,
      ValueType valueType,
      Intent intent,
      TypedRecordProcessor<?> processor) {
    eventProcessors.put(recordType, valueType, intent.value(), processor);

    return this;
  }

  public TypedEventStreamProcessorBuilder onCommand(
      ValueType valueType, Intent intent, TypedRecordProcessor<?> processor) {
    return onRecord(RecordType.COMMAND, valueType, intent, processor);
  }

  public <T extends UnpackedObject> TypedEventStreamProcessorBuilder onCommand(
      ValueType valueType, Intent intent, CommandProcessor<T> commandProcessor) {
    return onCommand(valueType, intent, new CommandProcessorImpl<>(commandProcessor));
  }

  public TypedEventStreamProcessorBuilder onRejection(
      ValueType valueType, Intent intent, TypedRecordProcessor<?> processor) {
    return onRecord(RecordType.COMMAND_REJECTION, valueType, intent, processor);
  }

  public TypedEventStreamProcessorBuilder withListener(StreamProcessorLifecycleAware listener) {
    this.lifecycleListeners.add(listener);
    return this;
  }

  /** Only required if a stream processor writes events to its own stream. */
  public TypedEventStreamProcessorBuilder keyGenerator(KeyGenerator keyGenerator) {
    this.keyGenerator = keyGenerator;
    return this;
  }

  public TypedEventStreamProcessorBuilder withStateController(
      final StateController stateController) {
    withListener(
        new StreamProcessorLifecycleAware() {
          @Override
          public void onClose() {
            stateController.close();
          }
        });
    return this;
  }

  public TypedStreamProcessor build() {

    return new TypedStreamProcessor(
        environment.getOutput(),
        eventProcessors,
        lifecycleListeners,
        environment.getEventRegistry(),
        keyGenerator,
        environment);
  }

  private static class DelegatingEventProcessor<T extends UnpackedObject>
      implements TypedRecordProcessor<T> {
    private final Function<TypedRecord<T>, TypedRecordProcessor<T>> dispatcher;
    private TypedRecordProcessor<T> selectedProcessor;

    DelegatingEventProcessor(Function<TypedRecord<T>, TypedRecordProcessor<T>> dispatcher) {
      this.dispatcher = dispatcher;
    }

    @Override
    public void processRecord(
        TypedRecord<T> record,
        TypedResponseWriter responseWriter,
        TypedStreamWriter streamWriter,
        Consumer<SideEffectProducer> sideEffect) {
      selectedProcessor = dispatcher.apply(record);
      if (selectedProcessor != null) {
        selectedProcessor.processRecord(record, responseWriter, streamWriter, sideEffect);
      }
    }
  }

  private static class ConsumerProcessor<T extends UnpackedObject>
      implements TypedRecordProcessor<T> {
    private final Consumer<T> consumer;

    ConsumerProcessor(Consumer<T> consumer) {
      this.consumer = consumer;
    }

    @Override
    public void processRecord(
        TypedRecord<T> record, TypedResponseWriter responseWriter, TypedStreamWriter streamWriter) {
      consumer.accept(record.getValue());
    }
  }
}

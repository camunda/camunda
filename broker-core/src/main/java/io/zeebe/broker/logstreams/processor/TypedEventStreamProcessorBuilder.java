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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import io.zeebe.logstreams.processor.EventLifecycleContext;
import io.zeebe.logstreams.snapshot.BaseValueSnapshotSupport;
import io.zeebe.logstreams.snapshot.ComposedSnapshot;
import io.zeebe.logstreams.snapshot.ZbMapSnapshotSupport;
import io.zeebe.logstreams.spi.ComposableSnapshotSupport;
import io.zeebe.logstreams.spi.SnapshotSupport;
import io.zeebe.map.ZbMap;
import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.msgpack.value.BaseValue;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.intent.Intent;

public class TypedEventStreamProcessorBuilder
{
    protected final TypedStreamEnvironment environment;

    protected List<ComposableSnapshotSupport> stateResources = new ArrayList<>();

    protected RecordProcessorMap eventProcessors = new RecordProcessorMap();
    protected List<StreamProcessorLifecycleAware> lifecycleListeners = new ArrayList<>();

    public TypedEventStreamProcessorBuilder(TypedStreamEnvironment environment)
    {
        this.environment = environment;
    }

    // TODO: could remove the ValueType argument as it follows from the intent
    public TypedEventStreamProcessorBuilder onEvent(ValueType valueType, Intent intent, TypedRecordProcessor<?> processor)
    {
        return onRecord(RecordType.EVENT, valueType, intent, processor);
    }

    public <T extends UnpackedObject> TypedEventStreamProcessorBuilder onEvent(ValueType valueType, Intent intent, Predicate<T> activationFunction, TypedRecordProcessor<T> processor)
    {
        return onEvent(valueType, intent, new DelegatingEventProcessor<T>(r -> activationFunction.test(r.getValue()) ? processor : null));
    }

    public TypedEventStreamProcessorBuilder onEvent(ValueType valueType, Intent intent, Consumer<? extends UnpackedObject> consumer)
    {
        return onEvent(valueType, intent, new ConsumerProcessor<>(consumer));
    }

    private TypedEventStreamProcessorBuilder onRecord(RecordType recordType, ValueType valueType, Intent intent, TypedRecordProcessor<?> processor)
    {
        eventProcessors.put(recordType, valueType, intent.value(), processor);

        return this;
    }

    public TypedEventStreamProcessorBuilder onCommand(ValueType valueType, Intent intent, TypedRecordProcessor<?> processor)
    {
        return onRecord(RecordType.COMMAND, valueType, intent, processor);
    }

    public <T extends UnpackedObject> TypedEventStreamProcessorBuilder onCommand(ValueType valueType, Intent intent, CommandProcessor<T> commandProcessor)
    {
        return onCommand(valueType, intent, new CommandProcessorImpl<>(commandProcessor));
    }

    public TypedEventStreamProcessorBuilder onRejection(ValueType valueType, Intent intent, TypedRecordProcessor<?> processor)
    {
        return onRecord(RecordType.COMMAND_REJECTION, valueType, intent, processor);
    }

    public TypedEventStreamProcessorBuilder withListener(StreamProcessorLifecycleAware listener)
    {
        this.lifecycleListeners.add(listener);
        return this;
    }

    public TypedEventStreamProcessorBuilder withStateResource(ZbMap<?, ?> map)
    {
        this.stateResources.add(new ZbMapSnapshotSupport<>(map));
        withListener(new StreamProcessorLifecycleAware()
        {
            @Override
            public void onClose()
            {
                map.close();
            }
        });
        return this;
    }

    public TypedEventStreamProcessorBuilder withStateResource(BaseValue value)
    {
        this.stateResources.add(new BaseValueSnapshotSupport(value));
        return this;
    }

    public TypedEventStreamProcessorBuilder withStateResource(ComposableSnapshotSupport snapshotSupport)
    {
        this.stateResources.add(snapshotSupport);
        return this;
    }

    public TypedStreamProcessor build()
    {

        final SnapshotSupport snapshotSupport;
        if (!stateResources.isEmpty())
        {
            snapshotSupport = new ComposedSnapshot(
                    stateResources.toArray(new ComposableSnapshotSupport[stateResources.size()]));
        }
        else
        {
            snapshotSupport = new NoopSnapshotSupport();
        }

        return new TypedStreamProcessor(
                snapshotSupport,
                environment.getOutput(),
                eventProcessors,
                lifecycleListeners,
                environment.getEventRegistry(),
                environment);
    }

    private static class DelegatingEventProcessor<T extends UnpackedObject> implements TypedRecordProcessor<T>
    {
        private Function<TypedRecord<T>, TypedRecordProcessor<T>> dispatcher;
        private TypedRecordProcessor<T> selectedProcessor;

        DelegatingEventProcessor(Function<TypedRecord<T>, TypedRecordProcessor<T>> dispatcher)
        {
            this.dispatcher = dispatcher;
        }

        @Override
        public void processRecord(TypedRecord<T> record)
        {
            selectedProcessor = dispatcher.apply(record);
            if (selectedProcessor != null)
            {
                selectedProcessor.processRecord(record);
            }
        }

        @Override
        public void processRecord(TypedRecord<T> record, EventLifecycleContext ctx)
        {
            selectedProcessor = dispatcher.apply(record);
            if (selectedProcessor != null)
            {
                selectedProcessor.processRecord(record, ctx);
            }
        }

        @Override
        public boolean executeSideEffects(TypedRecord<T> record, TypedResponseWriter responseWriter)
        {
            return selectedProcessor != null ? selectedProcessor.executeSideEffects(record, responseWriter) : true;
        }

        @Override
        public long writeRecord(TypedRecord<T> record, TypedStreamWriter writer)
        {
            return selectedProcessor != null ? selectedProcessor.writeRecord(record, writer) : 0L;
        }

        @Override
        public void updateState(TypedRecord<T> record)
        {
            if (selectedProcessor != null)
            {
                selectedProcessor.updateState(record);
            }
        }
    }

    private static class ConsumerProcessor<T extends UnpackedObject> implements TypedRecordProcessor<T>
    {
        private final Consumer<T> consumer;

        ConsumerProcessor(Consumer<T> consumer)
        {
            this.consumer = consumer;
        }

        @Override
        public void processRecord(TypedRecord<T> record)
        {
            consumer.accept(record.getValue());
        }
    }
}

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
import java.util.EnumMap;
import java.util.List;

import io.zeebe.logstreams.snapshot.ComposedSnapshot;
import io.zeebe.logstreams.snapshot.UnpackedObjectSnapshotSupport;
import io.zeebe.logstreams.snapshot.ZbMapSnapshotSupport;
import io.zeebe.logstreams.spi.ComposableSnapshotSupport;
import io.zeebe.logstreams.spi.SnapshotSupport;
import io.zeebe.map.ZbMap;
import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.protocol.clientapi.EventType;

@SuppressWarnings({"rawtypes", "unchecked"})
public class TypedEventStreamProcessorBuilder
{
    protected final TypedStreamEnvironment environment;

    protected List<ComposableSnapshotSupport> stateResources = new ArrayList<>();

    protected EnumMap<EventType, EnumMap> eventProcessors = new EnumMap<>(EventType.class);
    protected List<StreamProcessorLifecycleAware> lifecycleListeners = new ArrayList<>();

    public TypedEventStreamProcessorBuilder(TypedStreamEnvironment environment)
    {
        this.environment = environment;
    }

    public TypedEventStreamProcessorBuilder onEvent(EventType eventType, Enum state, TypedEventProcessor<?> processor)
    {
        EnumMap processorsForType = eventProcessors.get(eventType);
        if (processorsForType == null)
        {
            processorsForType = new EnumMap<>(state.getClass());
            eventProcessors.put(eventType, processorsForType);
        }

        processorsForType.put(state, processor);

        return this;
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

    public TypedEventStreamProcessorBuilder withStateResource(UnpackedObject object)
    {
        this.stateResources.add(new UnpackedObjectSnapshotSupport(object));
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
                environment.getEventRegistry());
    }
}

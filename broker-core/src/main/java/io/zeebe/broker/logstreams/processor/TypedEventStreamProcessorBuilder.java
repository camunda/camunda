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

import io.zeebe.logstreams.snapshot.ComposedZbMapSnapshot;
import io.zeebe.logstreams.snapshot.ZbMapSnapshotSupport;
import io.zeebe.logstreams.spi.SnapshotSupport;
import io.zeebe.map.ZbMap;
import io.zeebe.protocol.clientapi.EventType;

@SuppressWarnings({"rawtypes", "unchecked"})
public class TypedEventStreamProcessorBuilder
{
    protected final TypedStreamEnvironment environment;

    protected List<ZbMap<?, ?>> zbMaps = new ArrayList<>();

    protected EnumMap<EventType, EnumMap> eventProcessors = new EnumMap<>(EventType.class);

    protected List<Runnable> closeOperations = new ArrayList<>();


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

    public TypedEventStreamProcessorBuilder onClose(Runnable onClose)
    {
        this.closeOperations.add(onClose);
        return this;
    }

    public TypedEventStreamProcessorBuilder withStateResource(ZbMap<?, ?> map)
    {
        this.zbMaps.add(map);
        onClose(() -> map.close());
        return this;
    }

    public TypedStreamProcessor build()
    {

        final SnapshotSupport snapshotSupport;
        if (!zbMaps.isEmpty())
        {
            final ZbMapSnapshotSupport[] mapSupport = zbMaps
                    .stream()
                    .map(m -> new ZbMapSnapshotSupport<>(m))
                    .toArray(n -> new ZbMapSnapshotSupport[n]);
            snapshotSupport = new ComposedZbMapSnapshot(mapSupport);
        }
        else
        {
            snapshotSupport = new NoopSnapshotSupport();
        }

        return new TypedStreamProcessor(
                snapshotSupport,
                environment.getOutput(),
                eventProcessors,
                environment.getEventRegistry(),
                closeOperations);
    }
}

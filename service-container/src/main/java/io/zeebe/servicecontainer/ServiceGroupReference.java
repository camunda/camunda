/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.servicecontainer;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.BiConsumer;

public final class ServiceGroupReference<S>
{
    @SuppressWarnings("rawtypes")
    private static final BiConsumer NOOP_CONSUMER = (n, v) ->
    {
        // ignore
    };

    protected BiConsumer<ServiceName<S>, S> addHandler;
    protected BiConsumer<ServiceName<S>, S> removeHandler;
    protected final Map<ServiceName<S>, S> injectedValues = new HashMap<>();

    @SuppressWarnings("unchecked")
    private ServiceGroupReference()
    {
        this(NOOP_CONSUMER, NOOP_CONSUMER);
    }

    private ServiceGroupReference(BiConsumer<ServiceName<S>, S> addHandler, BiConsumer<ServiceName<S>, S> removeHandler)
    {
        this.addHandler = addHandler;
        this.removeHandler = removeHandler;
    }

    public void addValue(ServiceName<S> name, S value)
    {
        invoke(addHandler, name, value);
        injectedValues.put(name, value);
    }

    public void removeValue(ServiceName<S> name, S value)
    {
        invoke(removeHandler, name, value);
        injectedValues.remove(name);
    }

    public void uninject()
    {
        for (Entry<ServiceName<S>, S> e : injectedValues.entrySet())
        {
            invoke(removeHandler, e.getKey(), e.getValue());
        }
        injectedValues.clear();
    }

    private static <S> void invoke(BiConsumer<ServiceName<S>, S> consumer, ServiceName<S> name, S value)
    {
        consumer.accept(name, value);
    }

    public static <S> ServiceGroupReference<S> collection(Collection<S> collection)
    {
        final BiConsumer<ServiceName<S>, S> addHandler = (name, v) -> collection.add(v);
        final BiConsumer<ServiceName<S>, S> removeHandler = (name, v) -> collection.remove(v);

        return new ServiceGroupReference<>(addHandler, removeHandler);
    }

    public static <S, K> ServiceGroupReference<S> map(Map<ServiceName<S>, S> map)
    {
        final BiConsumer<ServiceName<S>, S> addHandler = (name, v) -> map.put(name, v);
        final BiConsumer<ServiceName<S>, S> removeHandler = (name, v) -> map.remove(name, v);

        return new ServiceGroupReference<>(addHandler, removeHandler);
    }

    public static <S> ReferenceBuilder<S> create()
    {
        return new ReferenceBuilder<>();
    }

    public static class ReferenceBuilder<S>
    {
        protected final ServiceGroupReference<S> referenceCollection = new ServiceGroupReference<>();

        public ReferenceBuilder<S> onRemove(BiConsumer<ServiceName<S>, S> removeConsumer)
        {
            referenceCollection.removeHandler = removeConsumer;
            return this;
        }

        public ReferenceBuilder<S> onAdd(BiConsumer<ServiceName<S>, S> addConsumer)
        {
            referenceCollection.addHandler = addConsumer;
            return this;
        }

        public ServiceGroupReference<S> build()
        {
            return referenceCollection;
        }
    }
}

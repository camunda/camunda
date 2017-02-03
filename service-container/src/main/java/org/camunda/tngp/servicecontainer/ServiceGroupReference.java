package org.camunda.tngp.servicecontainer;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.BiConsumer;

public class ServiceGroupReference<S>
{
    @SuppressWarnings("rawtypes")
    private final static BiConsumer NOOP_CONSUMER = (n, v) ->
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

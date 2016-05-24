package org.camunda.tngp.broker.system;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.camunda.tngp.broker.transport.worker.spi.ResourceContext;
import org.camunda.tngp.broker.transport.worker.spi.ResourceContextProvider;
import org.camunda.tngp.servicecontainer.ServiceListener;
import org.camunda.tngp.servicecontainer.ServiceName;

import uk.co.real_logic.agrona.collections.Int2ObjectHashMap;

@SuppressWarnings("unchecked")
public abstract class AbstractResourceContextProvider<C extends ResourceContext> implements ResourceContextProvider<C>, ServiceListener
{
    protected final Int2ObjectHashMap<C> contextMap = new Int2ObjectHashMap<>();
    protected C[] contexts;

    private final Class<C> type;

    public AbstractResourceContextProvider(Class<C> type)
    {
        this.type = type;
        contexts = (C[]) Array.newInstance(type, 0);
    }

    @Override
    public synchronized <S> void onServiceStarted(ServiceName<S> name, S service)
    {
        final C context = (C) service;
        contextMap.put(context.getResourceId(), context);

        final List<C> list = new ArrayList<C>(Arrays.asList(contexts));
        list.add(context);
        this.contexts = list.toArray((C[]) Array.newInstance(type, list.size()));
    }

    @Override
    public synchronized <S> void onServiceStopping(ServiceName<S> name, S service)
    {
        final C context = (C) service;
        contextMap.remove(context.getResourceId(), context);

        final List<C> list = new ArrayList<C>(Arrays.asList(contexts));
        list.remove(context);
        this.contexts = list.toArray((C[]) Array.newInstance(type, list.size()));
    }

    @Override
    public C getContextForResource(int id)
    {
        return contextMap.get(id);
    }

    public C[] getContexts()
    {
        return contexts;
    }

}

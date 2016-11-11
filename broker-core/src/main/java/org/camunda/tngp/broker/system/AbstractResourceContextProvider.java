package org.camunda.tngp.broker.system;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;

import org.camunda.tngp.broker.transport.worker.spi.ResourceContext;
import org.camunda.tngp.broker.transport.worker.spi.ResourceContextProvider;
import org.camunda.tngp.servicecontainer.ServiceGroupReference;

@SuppressWarnings("unchecked")
public abstract class AbstractResourceContextProvider<C extends ResourceContext> implements ResourceContextProvider<C>
{
    protected volatile C[] contexts;

    protected final ServiceGroupReference<C> resourceContextsReference;

    public AbstractResourceContextProvider(Class<C> type)
    {
        contexts = (C[]) Array.newInstance(type, 0);

        resourceContextsReference = ServiceGroupReference.<C>create()
            .onAdd((name, c) ->
            {
                final ArrayList<C> list = new ArrayList<>(Arrays.asList(contexts));
                list.add(c);
                contexts = list.toArray((C[]) Array.newInstance(type, list.size()));
            })
            .onRemove((name, c) ->
            {
                final ArrayList<C> list = new ArrayList<>(Arrays.asList(contexts));
                list.remove(c);
                contexts = list.toArray((C[]) Array.newInstance(type, list.size()));
            }).build();
    }

    @Override
    public C getContextForResource(int id)
    {
        final C[] copy = contexts;

        for (int i = 0; i < copy.length; i++)
        {
            final C c = copy[i];
            if (c.getResourceId() == id)
            {
                return c;
            }
        }

        return null;
    }

    public C[] getContexts()
    {
        return contexts;
    }

    public ServiceGroupReference<C> getResourceContextsReference()
    {
        return resourceContextsReference;
    }

}

package org.camunda.tngp.broker.event.handler;

import java.util.function.Supplier;

public class StaticSupplier<T> implements Supplier<T>
{
    protected T[] values;
    protected int currentValue;

    public static <T> StaticSupplier<T> returnInOrder(T... args)
    {
        final StaticSupplier<T> supplier = new StaticSupplier<>();
        supplier.values = args;
        supplier.currentValue = 0;

        return supplier;
    }

    @Override
    public T get()
    {
        if (currentValue < values.length)
        {
            final T value = values[currentValue];
            currentValue++;
            return value;
        }
        else
        {
            throw new RuntimeException("does not compute");
        }
    }

}

package org.camunda.tngp.util;

@FunctionalInterface
public interface CheckedConsumer<T>
{
    void accept(T t) throws Exception;
}

package io.zeebe.util;

@FunctionalInterface
public interface BiLongConsumer<T>
{
    void accept(T param1, long param2);
}
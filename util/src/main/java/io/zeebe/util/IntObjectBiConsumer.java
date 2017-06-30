package io.zeebe.util;

public interface IntObjectBiConsumer<T>
{
    void accept(int arg1, T arg2);
}

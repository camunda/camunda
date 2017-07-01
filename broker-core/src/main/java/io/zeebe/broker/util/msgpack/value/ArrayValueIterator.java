package io.zeebe.broker.util.msgpack.value;

import java.util.Iterator;

public interface ArrayValueIterator<T> extends Iterator<T>
{
    T add();
}

package org.camunda.tngp.log;

import java.util.concurrent.Future;

public interface Log extends AutoCloseable
{
    int getId();

    void close();

    long getInitialPosition();

    Future<Void> closeAsync();
}

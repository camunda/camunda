package org.camunda.tngp.broker.services;

import org.camunda.tngp.hashindex.HashIndex;

public interface HashIndexManager<I extends HashIndex<?, ?>>
{
    I getIndex();

    void writeCheckPoint(long logPosition);

    long getLastCheckpointPosition();
}

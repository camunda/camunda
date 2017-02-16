package org.camunda.tngp.hashindex.benchmarks;

import org.camunda.tngp.hashindex.Long2LongHashIndex;
import org.camunda.tngp.hashindex.store.FileChannelIndexStore;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;


@State(Scope.Benchmark)
public class Long2LongHashIndexSupplier
{
    Long2LongHashIndex index;
    FileChannelIndexStore tempFileIndexStore;

    @Setup(Level.Iteration)
    public void createIndex()
    {
        tempFileIndexStore = FileChannelIndexStore.tempFileIndexStore();
        index = new Long2LongHashIndex(tempFileIndexStore, (int) Math.pow(2, 16), 64);
    }

    @TearDown(Level.Iteration)
    public void closeIndex()
    {
        tempFileIndexStore.close();
    }

}

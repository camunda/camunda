package org.camunda.tngp.hashindex.benchmarks;

import java.util.HashMap;

import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

@State(Scope.Benchmark)
public class Long2LongHashMapSupplier
{

    HashMap<Long, Long> index;

    long keyCounter;

    @Setup
    public void createHashmap()
    {
        index = new HashMap<>();
    }

}

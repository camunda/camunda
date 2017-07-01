package io.zeebe.hashindex.benchmarks;

import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

@State(Scope.Benchmark)
public class LinearKeysSupplier
{
    long[] keys = new long[Benchmarks.DATA_SET_SIZE];

    @Setup
    public void generateKeys()
    {
        for (int k = 0; k < keys.length; k++)
        {
            keys[k] = k;
        }
    }


}

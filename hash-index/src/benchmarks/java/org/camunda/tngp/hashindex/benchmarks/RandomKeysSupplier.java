package org.camunda.tngp.hashindex.benchmarks;

import java.util.Random;

import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

@State(Scope.Benchmark)
public class RandomKeysSupplier
{
    long[] keys = new long[1000];

    @Setup
    public void generateKeys()
    {
        final Random random = new Random();

        for (int k = 0; k < keys.length; k++)
        {
            keys[k] = Math.abs(random.nextLong());
        }
    }


}

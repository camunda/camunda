package io.zeebe.hashindex.benchmarks;

import java.util.Random;

import org.openjdk.jmh.annotations.*;

@State(Scope.Benchmark)
public class RandomKeysSupplier
{
    long[] keys = new long[Benchmarks.DATA_SET_SIZE];

    @Setup
    public void generateKeys()
    {
        final Random random = new Random();

        for (int k = 0; k < keys.length; k++)
        {
            keys[k] = Math.min(Math.abs(random.nextLong()), Benchmarks.DATA_SET_SIZE - 1);
        }
    }


}

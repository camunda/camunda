package io.zeebe.hashindex.benchmarks;

import java.util.HashMap;

import io.zeebe.hashindex.Long2LongHashIndex;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Threads;

@BenchmarkMode(Mode.Throughput)
public class Long2LongHashIndexBenchmark
{

    @Benchmark
    @Threads(1)
    public long randomKeys(Long2LongHashIndexSupplier hashIndexSupplier, RandomKeysSupplier randomKeysSupplier)
    {
        final Long2LongHashIndex index = hashIndexSupplier.index;
        final long[] keys = randomKeysSupplier.keys;

        for (int i = 0; i < keys.length; i++)
        {
            index.put(keys[i], i);
        }

        long result = 0;

        for (int i = 0; i < keys.length; i++)
        {
            result += index.get(keys[i], -1);
        }

        return result;
    }

    @Benchmark
    @Threads(1)
    public long linearKeys(Long2LongHashIndexSupplier hashIndexSupplier, LinearKeysSupplier keysSupplier)
    {
        final Long2LongHashIndex index = hashIndexSupplier.index;
        final long[] keys = keysSupplier.keys;

        for (int i = 0; i < keys.length; i++)
        {
            index.put(keys[i], i);
        }

        long result = 0;

        for (int i = 0; i < keys.length; i++)
        {
            result += index.get(keys[i], -1);
        }

        return result;
    }

    @Benchmark
    @Threads(1)
    public long baselineLinearKeys(Long2LongHashMapSupplier hashMapSupplier, LinearKeysSupplier keysSupplier)
    {
        final HashMap<Long, Long> index = hashMapSupplier.index;
        final long[] keys = keysSupplier.keys;

        for (int i = 0; i < keys.length; i++)
        {
            index.put(keys[i], (long) i);
        }

        long result = 0;

        for (int i = 0; i < keys.length; i++)
        {
            result += index.get(keys[i]);
        }

        return result;
    }


    @Benchmark
    @Threads(1)
    public long baselineRandomKeys(Long2LongHashMapSupplier hashMapSupplier, RandomKeysSupplier keysSupplier)
    {
        final HashMap<Long, Long> index = hashMapSupplier.index;
        final long[] keys = keysSupplier.keys;

        for (int i = 0; i < keys.length; i++)
        {
            index.put(keys[i], (long) i);
        }

        long result = 0;

        for (int i = 0; i < keys.length; i++)
        {
            result += index.get(keys[i]);
        }

        return result;
    }


}
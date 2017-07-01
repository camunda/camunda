package io.zeebe.hashindex.benchmarks;

import java.io.IOException;

import io.zeebe.hashindex.Long2LongHashIndex;
import org.agrona.BitUtil;
import org.openjdk.jmh.annotations.*;


@State(Scope.Benchmark)
public class Long2LongHashIndexSupplier
{
    Long2LongHashIndex index;

    @Setup(Level.Iteration)
    public void createIndex() throws IOException
    {
        final int entriesPerBlock = 16;
        index = new Long2LongHashIndex(BitUtil.findNextPositivePowerOfTwo(Benchmarks.DATA_SET_SIZE / entriesPerBlock), entriesPerBlock);
    }

    @TearDown(Level.Iteration)
    public void closeIndex()
    {
        index.close();
    }

}

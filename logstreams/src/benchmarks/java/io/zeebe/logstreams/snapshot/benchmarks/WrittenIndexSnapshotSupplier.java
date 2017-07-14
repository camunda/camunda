/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.logstreams.snapshot.benchmarks;

import io.zeebe.hashindex.Long2LongHashIndex;
import io.zeebe.logstreams.snapshot.ComposedHashIndexSnapshot;
import io.zeebe.logstreams.snapshot.HashIndexSnapshotSupport;
import org.openjdk.jmh.annotations.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Random;

import static io.zeebe.hashindex.HashIndex.OPTIMAL_BUCKET_COUNT;
import static io.zeebe.hashindex.HashIndex.OPTIMAL_INDEX_SIZE;

/**
 *
 */
@State(Scope.Benchmark)
public class WrittenIndexSnapshotSupplier
{
    private Long2LongHashIndex index;

    File tmpFile;
    ComposedHashIndexSnapshot composedHashIndexSnapshot;

    @Setup(Level.Iteration)
    public void writeIndexSnapshot() throws Exception
    {
        tmpFile = new File("recoverIdxSnapshot-benchmark.txt");
        index = new Long2LongHashIndex(Benchmarks.DATA_SET_SIZE / OPTIMAL_BUCKET_COUNT, OPTIMAL_BUCKET_COUNT);

        final Random random = new Random();
        for (int idx = 0; idx < Benchmarks.DATA_SET_SIZE; idx++)
        {
            index.put(idx, Math.min(Math.abs(random.nextLong()), Benchmarks.DATA_SET_SIZE - 1));
        }
        composedHashIndexSnapshot = new ComposedHashIndexSnapshot(new HashIndexSnapshotSupport(index));

        composedHashIndexSnapshot.writeSnapshot(new FileOutputStream(tmpFile));
    }

    @TearDown(Level.Iteration)
    public void closeIndex()
    {
        index.close();
        tmpFile.delete();
    }
}

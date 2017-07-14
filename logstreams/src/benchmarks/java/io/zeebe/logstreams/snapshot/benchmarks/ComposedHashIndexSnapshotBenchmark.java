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

import io.zeebe.logstreams.snapshot.ComposedHashIndexSnapshot;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Threads;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;


@BenchmarkMode(Mode.SingleShotTime)
public class ComposedHashIndexSnapshotBenchmark
{
    @Benchmark
    @Threads(1)
    public long writeSnapshot(FilledIndexSnapshotSupplier filledIndexSnapshotSupplier) throws Exception
    {
        // given
        final File tmpFile = filledIndexSnapshotSupplier.tmpFile;
        final FileOutputStream outputStream = new FileOutputStream(tmpFile);
        final ComposedHashIndexSnapshot composedHashIndexSnapshot = filledIndexSnapshotSupplier.composedHashIndexSnapshot;

        // run
        composedHashIndexSnapshot.writeSnapshot(outputStream);

        // some bytes are writen
        return composedHashIndexSnapshot.getProcessedBytes();
    }

    @Benchmark
    @Threads(1)
    public long recoverSnapshot(WrittenIndexSnapshotSupplier writtenIndexSnapshotSupplier) throws Exception
    {
        // given
        final File tmpFile = writtenIndexSnapshotSupplier.tmpFile;
        final FileInputStream outputStream = new FileInputStream(tmpFile);
        final ComposedHashIndexSnapshot composedHashIndexSnapshot = writtenIndexSnapshotSupplier.composedHashIndexSnapshot;

        // run
        composedHashIndexSnapshot.recoverFromSnapshot(outputStream);

        // some bytes are writen
        return composedHashIndexSnapshot.getProcessedBytes();
    }
}

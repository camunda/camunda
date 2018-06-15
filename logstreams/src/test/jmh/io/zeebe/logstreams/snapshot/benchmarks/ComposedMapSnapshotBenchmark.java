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

import java.io.*;

import io.zeebe.logstreams.snapshot.ComposedSnapshot;
import org.openjdk.jmh.annotations.*;


@BenchmarkMode(Mode.SingleShotTime)
public class ComposedMapSnapshotBenchmark
{
    @Benchmark
    @Threads(1)
    public long writeSnapshot(FilledMapSnapshotSupplier filledMapSnapshotSupplier) throws Exception
    {
        // given
        final File tmpFile = filledMapSnapshotSupplier.tmpFile;
        final FileOutputStream outputStream = new FileOutputStream(tmpFile);
        final ComposedSnapshot composedZbMapSnapshot = filledMapSnapshotSupplier.composedZbMapSnapshot;

        // run
        composedZbMapSnapshot.writeSnapshot(outputStream);
        outputStream.flush();

        // some bytes are written
        return composedZbMapSnapshot.getProcessedBytes();
    }

    @Benchmark
    @Threads(1)
    public long recoverSnapshot(WrittenMapSnapshotSupplier writtenMapSnapshotSupplier) throws Exception
    {
        // given
        final File tmpFile = writtenMapSnapshotSupplier.tmpFile;
        final FileInputStream outputStream = new FileInputStream(tmpFile);
        final ComposedSnapshot composedZbMapSnapshot = writtenMapSnapshotSupplier.composedZbMapSnapshot;

        // run
        composedZbMapSnapshot.recoverFromSnapshot(outputStream);

        // some bytes are read
        return composedZbMapSnapshot.getProcessedBytes();
    }
}

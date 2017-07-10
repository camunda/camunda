/**
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

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
package io.zeebe.map.benchmarks;

import java.io.IOException;

import io.zeebe.map.Long2LongZbMap;
import org.agrona.BitUtil;
import org.openjdk.jmh.annotations.*;


@State(Scope.Benchmark)
public class Long2LongZbMapSupplier
{
    Long2LongZbMap map;

    @Setup(Level.Iteration)
    public void createmap() throws IOException
    {
        final int entriesPerBlock = 16;
        map = new Long2LongZbMap(BitUtil.findNextPositivePowerOfTwo(Benchmarks.DATA_SET_SIZE / entriesPerBlock), entriesPerBlock);
    }

    @TearDown(Level.Iteration)
    public void closemap()
    {
        map.close();
    }

}
